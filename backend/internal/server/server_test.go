package server_test

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/actiontracker/backend/internal/provider"
	"github.com/actiontracker/backend/internal/server"
)

// stubPinger is an injectable health dependency.
type stubPinger struct {
	err error
}

func (p stubPinger) Ping(context.Context) error { return p.err }

// stubLLM returns canned values so handler wiring can be exercised without a
// real provider.
type stubLLM struct {
	text        string
	suggestions []string
	description string
	actions     []provider.ExtractedAction
	err         error
}

func (s stubLLM) NotificationText(context.Context, []provider.ActionItemSummary) (string, error) {
	return s.text, s.err
}
func (s stubLLM) SuggestActions(context.Context, string) ([]string, error) {
	return s.suggestions, s.err
}
func (s stubLLM) Describe(context.Context, string) (string, error) {
	return s.description, s.err
}
func (s stubLLM) ExtractActions(context.Context, string) ([]provider.ExtractedAction, error) {
	return s.actions, s.err
}

type stubTranscription struct {
	transcript string
	err        error
}

func (s stubTranscription) Transcribe(context.Context, []byte, string) (string, error) {
	return s.transcript, s.err
}

func TestHealthz(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name       string
		db         server.Pinger
		wantStatus int
		wantBodyDB string
	}{
		{
			name:       "no database wired reports not_configured and 200",
			db:         nil,
			wantStatus: http.StatusOK,
			wantBodyDB: "not_configured",
		},
		{
			name:       "healthy database reports ok and 200",
			db:         stubPinger{err: nil},
			wantStatus: http.StatusOK,
			wantBodyDB: "ok",
		},
		{
			name:       "unreachable database reports unavailable and 503",
			db:         stubPinger{err: errors.New("connection refused")},
			wantStatus: http.StatusServiceUnavailable,
			wantBodyDB: "unavailable",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			opts := []server.Option{}
			if tt.db != nil {
				opts = append(opts, server.WithDB(tt.db))
			}
			srv := server.New("127.0.0.1:0", opts...)

			req := httptest.NewRequest(http.MethodGet, "/healthz", nil)
			rec := httptest.NewRecorder()
			srv.Handler().ServeHTTP(rec, req)

			if rec.Code != tt.wantStatus {
				t.Fatalf("status = %d, want %d", rec.Code, tt.wantStatus)
			}

			var body struct {
				Status   string `json:"status"`
				Database string `json:"database"`
			}
			if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
				t.Fatalf("decoding body: %v", err)
			}
			if body.Database != tt.wantBodyDB {
				t.Errorf("database = %q, want %q", body.Database, tt.wantBodyDB)
			}
		})
	}
}

func TestLLMProxyHandlers(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name       string
		path       string
		body       string
		llm        provider.LLMProvider
		wantStatus int
		wantSubstr string
	}{
		{
			name:       "notification-text success",
			path:       "/llm/notification-text",
			body:       `{"items":[{"title":"Book flight","bucketName":"Travel","dueLabel":"today"}]}`,
			llm:        stubLLM{text: "Don't forget to book your flight"},
			wantStatus: http.StatusOK,
			wantSubstr: "book your flight",
		},
		{
			name:       "suggest-actions success",
			path:       "/llm/suggest-actions",
			body:       `{"itemTitle":"Learn Go"}`,
			llm:        stubLLM{suggestions: []string{"Read the tour", "Build a service"}},
			wantStatus: http.StatusOK,
			wantSubstr: "Build a service",
		},
		{
			name:       "describe success",
			path:       "/llm/describe",
			body:       `{"content":"a reel about pasta"}`,
			llm:        stubLLM{description: "A recipe for fresh pasta"},
			wantStatus: http.StatusOK,
			wantSubstr: "fresh pasta",
		},
		{
			name:       "provider unavailable returns 503",
			path:       "/llm/describe",
			body:       `{"content":"x"}`,
			llm:        stubLLM{err: provider.ErrUnavailable},
			wantStatus: http.StatusServiceUnavailable,
			wantSubstr: "unavailable",
		},
		{
			name:       "invalid json returns 400",
			path:       "/llm/suggest-actions",
			body:       `{not json`,
			llm:        stubLLM{},
			wantStatus: http.StatusBadRequest,
			wantSubstr: "invalid request body",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			srv := server.New("127.0.0.1:0", server.WithLLMProvider(tt.llm))
			req := httptest.NewRequest(http.MethodPost, tt.path, strings.NewReader(tt.body))
			rec := httptest.NewRecorder()
			srv.Handler().ServeHTTP(rec, req)

			if rec.Code != tt.wantStatus {
				t.Fatalf("status = %d, want %d (body: %s)", rec.Code, tt.wantStatus, rec.Body.String())
			}
			if !strings.Contains(rec.Body.String(), tt.wantSubstr) {
				t.Errorf("body %q does not contain %q", rec.Body.String(), tt.wantSubstr)
			}
		})
	}
}

func TestTranscribeHandler(t *testing.T) {
	t.Parallel()

	tests := []struct {
		name       string
		stt        provider.TranscriptionProvider
		wantStatus int
		wantSubstr string
	}{
		{
			name:       "success returns transcript",
			stt:        stubTranscription{transcript: "hello world"},
			wantStatus: http.StatusOK,
			wantSubstr: "hello world",
		},
		{
			name:       "unavailable returns 503",
			stt:        stubTranscription{err: provider.ErrUnavailable},
			wantStatus: http.StatusServiceUnavailable,
			wantSubstr: "unavailable",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			t.Parallel()

			srv := server.New("127.0.0.1:0", server.WithTranscriptionProvider(tt.stt))
			req := httptest.NewRequest(http.MethodPost, "/transcription/transcribe", strings.NewReader("fake-audio-bytes"))
			req.Header.Set("Content-Type", "audio/m4a")
			rec := httptest.NewRecorder()
			srv.Handler().ServeHTTP(rec, req)

			if rec.Code != tt.wantStatus {
				t.Fatalf("status = %d, want %d", rec.Code, tt.wantStatus)
			}
			if !strings.Contains(rec.Body.String(), tt.wantSubstr) {
				t.Errorf("body %q does not contain %q", rec.Body.String(), tt.wantSubstr)
			}
		})
	}
}

// TestDefaultProvidersFailSoft verifies that with no provider injected the
// proxy endpoints fail soft (503) rather than panicking.
func TestDefaultProvidersFailSoft(t *testing.T) {
	t.Parallel()

	srv := server.New("127.0.0.1:0")
	req := httptest.NewRequest(http.MethodPost, "/llm/notification-text", strings.NewReader(`{"items":[]}`))
	rec := httptest.NewRecorder()
	srv.Handler().ServeHTTP(rec, req)

	if rec.Code != http.StatusServiceUnavailable {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusServiceUnavailable)
	}
}
