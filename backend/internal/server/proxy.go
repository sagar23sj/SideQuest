package server

import (
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"

	"github.com/actiontracker/backend/internal/provider"
)

// maxProxyBodyBytes bounds request bodies for proxy endpoints to limit memory
// use and abuse (golang-security: limit everything).
const maxProxyBodyBytes = 25 << 20 // 25 MiB (covers short audio clips)

// notificationTextRequest is the client payload for POST /llm/notification-text.
type notificationTextRequest struct {
	Items []provider.ActionItemSummary `json:"items"`
}

type notificationTextResponse struct {
	Text string `json:"text"`
}

func (s *Server) handleNotificationText(w http.ResponseWriter, r *http.Request) {
	var req notificationTextRequest
	if !decodeJSON(w, r, s.logger, &req) {
		return
	}

	ctx, cancel := s.providerContext(r)
	defer cancel()

	text, err := s.llm.NotificationText(ctx, req.Items)
	if err != nil {
		s.writeProviderError(w, "notification-text", err)
		return
	}
	writeJSON(w, s.logger, http.StatusOK, notificationTextResponse{Text: text})
}

type suggestActionsRequest struct {
	ItemTitle string `json:"itemTitle"`
}

type suggestActionsResponse struct {
	Suggestions []string `json:"suggestions"`
}

func (s *Server) handleSuggestActions(w http.ResponseWriter, r *http.Request) {
	var req suggestActionsRequest
	if !decodeJSON(w, r, s.logger, &req) {
		return
	}

	ctx, cancel := s.providerContext(r)
	defer cancel()

	suggestions, err := s.llm.SuggestActions(ctx, req.ItemTitle)
	if err != nil {
		s.writeProviderError(w, "suggest-actions", err)
		return
	}
	writeJSON(w, s.logger, http.StatusOK, suggestActionsResponse{Suggestions: suggestions})
}

type describeRequest struct {
	Content string `json:"content"`
}

type describeResponse struct {
	Description string `json:"description"`
}

func (s *Server) handleDescribe(w http.ResponseWriter, r *http.Request) {
	var req describeRequest
	if !decodeJSON(w, r, s.logger, &req) {
		return
	}

	ctx, cancel := s.providerContext(r)
	defer cancel()

	desc, err := s.llm.Describe(ctx, req.Content)
	if err != nil {
		s.writeProviderError(w, "describe", err)
		return
	}
	writeJSON(w, s.logger, http.StatusOK, describeResponse{Description: desc})
}

type extractActionsRequest struct {
	Transcript string `json:"transcript"`
}

type extractActionsResponse struct {
	Actions []provider.ExtractedAction `json:"actions"`
}

func (s *Server) handleExtractActions(w http.ResponseWriter, r *http.Request) {
	var req extractActionsRequest
	if !decodeJSON(w, r, s.logger, &req) {
		return
	}

	ctx, cancel := s.providerContext(r)
	defer cancel()

	actions, err := s.llm.ExtractActions(ctx, req.Transcript)
	if err != nil {
		s.writeProviderError(w, "extract-actions", err)
		return
	}
	writeJSON(w, s.logger, http.StatusOK, extractActionsResponse{Actions: actions})
}

type transcribeResponse struct {
	Transcript string `json:"transcript"`
}

// handleTranscribe proxies raw audio to the transcription provider. The audio
// is sent as the raw request body with the Content-Type header describing the
// encoding.
func (s *Server) handleTranscribe(w http.ResponseWriter, r *http.Request) {
	body := http.MaxBytesReader(w, r.Body, maxProxyBodyBytes)
	audio, err := io.ReadAll(body)
	if err != nil {
		// Body too large or read error: generic message to client.
		writeError(w, s.logger, http.StatusRequestEntityTooLarge, "audio payload too large or unreadable")
		return
	}

	ctx, cancel := s.providerContext(r)
	defer cancel()

	transcript, err := s.transcription.Transcribe(ctx, audio, r.Header.Get("Content-Type"))
	if err != nil {
		s.writeProviderError(w, "transcribe", err)
		return
	}
	writeJSON(w, s.logger, http.StatusOK, transcribeResponse{Transcript: transcript})
}

// writeProviderError maps provider errors to client responses. Provider
// unavailability is surfaced as 503 with a generic message; details are logged
// server-side. Provider keys are never included in any response.
func (s *Server) writeProviderError(w http.ResponseWriter, op string, err error) {
	if errors.Is(err, provider.ErrUnavailable) {
		s.logger.Warn("provider unavailable", slog.String("op", op), slog.Any("error", err))
		writeError(w, s.logger, http.StatusServiceUnavailable, "the requested feature is temporarily unavailable")
		return
	}
	s.logger.Error("provider call failed", slog.String("op", op), slog.Any("error", err))
	writeError(w, s.logger, http.StatusBadGateway, "the requested feature could not be completed")
}

// decodeJSON reads and decodes a JSON request body with a size limit. On error
// it writes a generic 400 and returns false.
func decodeJSON(w http.ResponseWriter, r *http.Request, logger *slog.Logger, dst any) bool {
	r.Body = http.MaxBytesReader(w, r.Body, maxProxyBodyBytes)
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(dst); err != nil {
		logger.Warn("invalid request body", slog.Any("error", err))
		writeError(w, logger, http.StatusBadRequest, "invalid request body")
		return false
	}
	return true
}
