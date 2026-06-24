// Package provider defines the pluggable adapters the backend uses to reach
// external AI services (LLM and speech-to-text) on behalf of clients.
//
// Provider API keys live exclusively server-side: they are read from config
// and held inside the adapter. They are never serialized into a response or
// returned to clients. Handlers depend on the LLMProvider and
// TranscriptionProvider interfaces (accept interfaces, inject dependencies),
// so providers can be swapped (OpenAI, Anthropic, Bedrock, a test fake)
// without touching handler code.
package provider

import (
	"context"
	"errors"
)

// ErrUnavailable indicates the provider could not service the request (network
// failure, upstream error, or not configured). Handlers translate this into a
// generic client-facing error and log details server-side.
var ErrUnavailable = errors.New("provider: unavailable")

// ActionItemSummary is a minimal view of an action item passed to the LLM for
// notification-text generation. It deliberately excludes account/secret data.
type ActionItemSummary struct {
	Title       string `json:"title"`
	BucketName  string `json:"bucketName"`
	DueLabel    string `json:"dueLabel"`
}

// ExtractedAction is one actionable item the LLM extracted from a transcript.
type ExtractedAction struct {
	Title             string  `json:"title"`
	SuggestedBucket   *string `json:"suggestedBucket,omitempty"`
}

// LLMProvider proxies large-language-model features. Every method takes a
// context so callers can bound the outbound call with a timeout.
type LLMProvider interface {
	// NotificationText generates reminder text for the given items.
	NotificationText(ctx context.Context, items []ActionItemSummary) (string, error)
	// SuggestActions returns suggested next actions for an item title.
	SuggestActions(ctx context.Context, itemTitle string) ([]string, error)
	// Describe summarizes shared content into a task description.
	Describe(ctx context.Context, content string) (string, error)
	// ExtractActions extracts actionable items from a transcript.
	ExtractActions(ctx context.Context, transcript string) ([]ExtractedAction, error)
}

// TranscriptionProvider proxies speech-to-text.
type TranscriptionProvider interface {
	// Transcribe converts audio bytes into a text transcript. The mimeType
	// (e.g. "audio/m4a") describes the audio encoding.
	Transcribe(ctx context.Context, audio []byte, mimeType string) (string, error)
}
