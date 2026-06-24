// Package server wires the HTTP API: routing, timeouts, the proxy handlers,
// and graceful shutdown. It depends on injected interfaces (DB pinger, LLM and
// transcription providers) so it can be constructed and tested in isolation.
package server

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"time"

	"github.com/actiontracker/backend/internal/provider"
)

// Pinger is the minimal database dependency the server needs for health
// checks. Accepting this narrow interface keeps the server decoupled from the
// concrete pool and easy to test.
type Pinger interface {
	Ping(ctx context.Context) error
}

// Server holds the HTTP server, its dependencies, and tunables.
type Server struct {
	httpServer    *http.Server
	logger        *slog.Logger
	db            Pinger
	llm           provider.LLMProvider
	transcription provider.TranscriptionProvider
	accounts      AccountService
	tokens        TokenIssuer

	addr                string
	readHeaderTimeout   time.Duration
	readTimeout         time.Duration
	writeTimeout        time.Duration
	idleTimeout         time.Duration
	externalCallTimeout time.Duration
	dbCallTimeout       time.Duration
}

// Option configures the Server.
type Option func(*Server)

// WithLogger injects a structured logger. Defaults to slog.Default.
func WithLogger(l *slog.Logger) Option {
	return func(s *Server) {
		if l != nil {
			s.logger = l
		}
	}
}

// WithDB injects the database health dependency.
func WithDB(p Pinger) Option {
	return func(s *Server) { s.db = p }
}

// WithLLMProvider injects the LLM proxy provider.
func WithLLMProvider(p provider.LLMProvider) Option {
	return func(s *Server) { s.llm = p }
}

// WithTranscriptionProvider injects the transcription proxy provider.
func WithTranscriptionProvider(p provider.TranscriptionProvider) Option {
	return func(s *Server) { s.transcription = p }
}

// WithAccountService injects the account use-case service (Req 13.1–13.3).
func WithAccountService(a AccountService) Option {
	return func(s *Server) { s.accounts = a }
}

// WithTokenIssuer injects the JWT token issuer used for login/refresh.
func WithTokenIssuer(t TokenIssuer) Option {
	return func(s *Server) { s.tokens = t }
}

// WithReadHeaderTimeout sets the header read timeout.
func WithReadHeaderTimeout(d time.Duration) Option {
	return func(s *Server) { s.readHeaderTimeout = d }
}

// WithReadTimeout sets the full request read timeout.
func WithReadTimeout(d time.Duration) Option {
	return func(s *Server) { s.readTimeout = d }
}

// WithWriteTimeout sets the response write timeout.
func WithWriteTimeout(d time.Duration) Option {
	return func(s *Server) { s.writeTimeout = d }
}

// WithIdleTimeout sets the keep-alive idle timeout.
func WithIdleTimeout(d time.Duration) Option {
	return func(s *Server) { s.idleTimeout = d }
}

// WithExternalCallTimeout bounds outbound provider (LLM/STT) calls.
func WithExternalCallTimeout(d time.Duration) Option {
	return func(s *Server) { s.externalCallTimeout = d }
}

// WithDBCallTimeout bounds database-backed handler calls (accounts).
func WithDBCallTimeout(d time.Duration) Option {
	return func(s *Server) {
		if d > 0 {
			s.dbCallTimeout = d
		}
	}
}

const (
	defaultReadHeaderTimeout   = 5 * time.Second
	defaultReadTimeout         = 15 * time.Second
	defaultWriteTimeout        = 30 * time.Second
	defaultIdleTimeout         = 120 * time.Second
	defaultExternalCallTimeout = 30 * time.Second
	defaultDBCallTimeout       = 10 * time.Second
)

// New builds a Server bound to addr with the given options. It does not start
// listening — call Run for that. Server timeouts are always set (never left at
// the unbounded zero value) per golang-security guidance.
func New(addr string, opts ...Option) *Server {
	s := &Server{
		logger:              slog.Default(),
		llm:                 noopLLM{},
		transcription:       noopTranscription{},
		addr:                addr,
		readHeaderTimeout:   defaultReadHeaderTimeout,
		readTimeout:         defaultReadTimeout,
		writeTimeout:        defaultWriteTimeout,
		idleTimeout:         defaultIdleTimeout,
		externalCallTimeout: defaultExternalCallTimeout,
		dbCallTimeout:       defaultDBCallTimeout,
	}
	for _, opt := range opts {
		opt(s)
	}

	s.httpServer = &http.Server{
		Addr:              s.addr,
		Handler:           s.routes(),
		ReadHeaderTimeout: s.readHeaderTimeout,
		ReadTimeout:       s.readTimeout,
		WriteTimeout:      s.writeTimeout,
		IdleTimeout:       s.idleTimeout,
		ErrorLog:          slog.NewLogLogger(s.logger.Handler(), slog.LevelError),
	}
	return s
}

// Handler exposes the configured router (useful for httptest in unit tests).
func (s *Server) Handler() http.Handler {
	return s.httpServer.Handler
}

// routes builds the ServeMux using Go 1.22+ method+path patterns.
func (s *Server) routes() http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /healthz", s.handleHealthz)

	// LLM proxy endpoints (keys held server-side in the provider adapter).
	mux.HandleFunc("POST /llm/notification-text", s.handleNotificationText)
	mux.HandleFunc("POST /llm/suggest-actions", s.handleSuggestActions)
	mux.HandleFunc("POST /llm/describe", s.handleDescribe)
	mux.HandleFunc("POST /llm/extract-actions", s.handleExtractActions)

	// Transcription proxy endpoint.
	mux.HandleFunc("POST /transcription/transcribe", s.handleTranscribe)

	// Accounts + auth (task 23; Req 13.1–13.3). POST /accounts replaces the
	// former 501 placeholder. Login/refresh issue and renew JWT pairs; the
	// refresh endpoint backs server-side silent refresh.
	mux.HandleFunc("POST /accounts", s.handleCreateAccount)
	mux.HandleFunc("POST /auth/login", s.handleLogin)
	mux.HandleFunc("POST /auth/refresh", s.handleRefresh)

	// Feature endpoints (sync=24, games=25, leaderboards=26) are added by
	// later tasks. Their routes are intentionally not registered here yet.

	return mux
}

// providerContext derives a timeout-bounded context from the request for
// outbound provider calls. The caller MUST call the returned cancel func.
func (s *Server) providerContext(r *http.Request) (context.Context, context.CancelFunc) {
	return context.WithTimeout(r.Context(), s.externalCallTimeout)
}

// Run starts serving and blocks until ctx is canceled, then shuts down
// gracefully within shutdownTimeout.
func (s *Server) Run(ctx context.Context, shutdownTimeout time.Duration) error {
	errCh := make(chan error, 1)
	go func() {
		s.logger.Info("http server listening", slog.String("addr", s.addr))
		if err := s.httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			errCh <- err
			return
		}
		errCh <- nil
	}()

	select {
	case err := <-errCh:
		return err
	case <-ctx.Done():
		s.logger.Info("shutdown signal received, draining connections")
		shutdownCtx, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
		defer cancel()
		return s.httpServer.Shutdown(shutdownCtx)
	}
}

// --- JSON helpers ---

func writeJSON(w http.ResponseWriter, logger *slog.Logger, status int, payload any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	if payload == nil {
		return
	}
	if err := json.NewEncoder(w).Encode(payload); err != nil {
		logger.Error("encoding response", slog.Any("error", err))
	}
}

// errorResponse is the structured client-facing error body (code + message).
type errorResponse struct {
	Error errorBody `json:"error"`
}

type errorBody struct {
	Status  int    `json:"status"`
	Message string `json:"message"`
}

// writeError returns a generic, structured error to the client. Detailed
// causes are logged at the call site, never sent to the client.
func writeError(w http.ResponseWriter, logger *slog.Logger, status int, message string) {
	writeJSON(w, logger, status, errorResponse{Error: errorBody{Status: status, Message: message}})
}

// --- no-op providers (used when none injected) ---

type noopLLM struct{}

func (noopLLM) NotificationText(context.Context, []provider.ActionItemSummary) (string, error) {
	return "", provider.ErrUnavailable
}
func (noopLLM) SuggestActions(context.Context, string) ([]string, error) {
	return nil, provider.ErrUnavailable
}
func (noopLLM) Describe(context.Context, string) (string, error) {
	return "", provider.ErrUnavailable
}
func (noopLLM) ExtractActions(context.Context, string) ([]provider.ExtractedAction, error) {
	return nil, provider.ErrUnavailable
}

type noopTranscription struct{}

func (noopTranscription) Transcribe(context.Context, []byte, string) (string, error) {
	return "", provider.ErrUnavailable
}
