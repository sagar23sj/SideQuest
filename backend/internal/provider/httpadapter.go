package provider

import (
	"context"
	"net/http"
	"time"
)

// HTTPLLMProvider is the default LLMProvider adapter. It targets an
// HTTP/JSON LLM API (OpenAI-/Bedrock-style). The API key is held here,
// server-side, and is never exposed to clients.
//
// This is a working skeleton: the wiring (key custody, injected client,
// per-call timeout) is in place. Concrete request/response marshaling for a
// specific provider is added in later tasks (7/15). When the provider is not
// configured (no apiKey/baseURL), methods return ErrUnavailable so the
// fail-soft client contract holds.
type HTTPLLMProvider struct {
	apiKey   string // secret, server-side only
	baseURL  string
	client   *http.Client
}

// LLMOption configures an HTTPLLMProvider.
type LLMOption func(*HTTPLLMProvider)

// WithLLMHTTPClient injects a custom HTTP client (e.g. one with transport-level
// timeouts or instrumentation). Injecting the client keeps the adapter
// testable.
func WithLLMHTTPClient(c *http.Client) LLMOption {
	return func(p *HTTPLLMProvider) { p.client = c }
}

// NewHTTPLLMProvider builds the default LLM adapter. apiKey and baseURL come
// from config (sourced from the environment). The adapter is usable even when
// unconfigured: it will report ErrUnavailable rather than panicking.
func NewHTTPLLMProvider(apiKey, baseURL string, opts ...LLMOption) *HTTPLLMProvider {
	p := &HTTPLLMProvider{
		apiKey:  apiKey,
		baseURL: baseURL,
		client:  &http.Client{Timeout: 30 * time.Second},
	}
	for _, opt := range opts {
		opt(p)
	}
	return p
}

// configured reports whether the adapter has the credentials/endpoint needed
// to make a real call.
func (p *HTTPLLMProvider) configured() bool {
	return p.apiKey != "" && p.baseURL != ""
}

// Compile-time check that the adapter satisfies the interface.
var _ LLMProvider = (*HTTPLLMProvider)(nil)

func (p *HTTPLLMProvider) NotificationText(ctx context.Context, items []ActionItemSummary) (string, error) {
	if !p.configured() {
		return "", ErrUnavailable
	}
	// TODO(task-15): marshal items, POST to p.baseURL with bearer p.apiKey,
	// honor ctx deadline, parse response. Until then, fail soft.
	return "", ErrUnavailable
}

func (p *HTTPLLMProvider) SuggestActions(ctx context.Context, itemTitle string) ([]string, error) {
	if !p.configured() {
		return nil, ErrUnavailable
	}
	// TODO(task-15): real provider call.
	return nil, ErrUnavailable
}

func (p *HTTPLLMProvider) Describe(ctx context.Context, content string) (string, error) {
	if !p.configured() {
		return "", ErrUnavailable
	}
	// TODO(task-15): real provider call.
	return "", ErrUnavailable
}

func (p *HTTPLLMProvider) ExtractActions(ctx context.Context, transcript string) ([]ExtractedAction, error) {
	if !p.configured() {
		return nil, ErrUnavailable
	}
	// TODO(task-20): real provider call.
	return nil, ErrUnavailable
}

// HTTPTranscriptionProvider is the default TranscriptionProvider adapter. The
// API key is held server-side and never exposed to clients.
type HTTPTranscriptionProvider struct {
	apiKey  string // secret, server-side only
	baseURL string
	client  *http.Client
}

// TranscriptionOption configures an HTTPTranscriptionProvider.
type TranscriptionOption func(*HTTPTranscriptionProvider)

// WithTranscriptionHTTPClient injects a custom HTTP client.
func WithTranscriptionHTTPClient(c *http.Client) TranscriptionOption {
	return func(p *HTTPTranscriptionProvider) { p.client = c }
}

// NewHTTPTranscriptionProvider builds the default STT adapter.
func NewHTTPTranscriptionProvider(apiKey, baseURL string, opts ...TranscriptionOption) *HTTPTranscriptionProvider {
	p := &HTTPTranscriptionProvider{
		apiKey:  apiKey,
		baseURL: baseURL,
		client:  &http.Client{Timeout: 60 * time.Second},
	}
	for _, opt := range opts {
		opt(p)
	}
	return p
}

func (p *HTTPTranscriptionProvider) configured() bool {
	return p.apiKey != "" && p.baseURL != ""
}

// Compile-time check that the adapter satisfies the interface.
var _ TranscriptionProvider = (*HTTPTranscriptionProvider)(nil)

func (p *HTTPTranscriptionProvider) Transcribe(ctx context.Context, audio []byte, mimeType string) (string, error) {
	if !p.configured() {
		return "", ErrUnavailable
	}
	// TODO(task-19): upload audio to p.baseURL with bearer p.apiKey, honor
	// ctx deadline, parse transcript. Until then, fail soft.
	return "", ErrUnavailable
}
