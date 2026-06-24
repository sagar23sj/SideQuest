package server

import (
	"context"
	"net/http"
	"time"
)

// contextWithTimeout derives a bounded context from the request. The caller
// MUST invoke the returned cancel func.
func contextWithTimeout(r *http.Request, d time.Duration) (context.Context, context.CancelFunc) {
	return context.WithTimeout(r.Context(), d)
}

// healthResponse is the body returned by GET /healthz.
type healthResponse struct {
	Status   string `json:"status"`
	Database string `json:"database"`
}

const (
	healthStatusOK         = "ok"
	healthStatusDegraded   = "degraded"
	dbStatusOK             = "ok"
	dbStatusUnavailable    = "unavailable"
	dbStatusNotConfigured  = "not_configured"
	healthDBCheckTimeout   = 2 * time.Second
)

// handleHealthz reports liveness and, when a database dependency is wired,
// readiness based on a bounded Ping. It returns 200 when healthy and 503 when
// the database is configured but unreachable.
func (s *Server) handleHealthz(w http.ResponseWriter, r *http.Request) {
	resp := healthResponse{Status: healthStatusOK, Database: dbStatusNotConfigured}

	if s.db != nil {
		ctx, cancel := contextWithTimeout(r, healthDBCheckTimeout)
		defer cancel()
		if err := s.db.Ping(ctx); err != nil {
			s.logger.Warn("healthz: database ping failed")
			resp.Status = healthStatusDegraded
			resp.Database = dbStatusUnavailable
			writeJSON(w, s.logger, http.StatusServiceUnavailable, resp)
			return
		}
		resp.Database = dbStatusOK
	}

	writeJSON(w, s.logger, http.StatusOK, resp)
}
