package provider_test

import (
	"context"
	"errors"
	"testing"

	"github.com/actiontracker/backend/internal/provider"
)

// TestUnconfiguredProvidersFailSoft verifies the default adapters report
// ErrUnavailable (rather than panicking or leaking keys) when no provider
// credentials are configured.
func TestUnconfiguredProvidersFailSoft(t *testing.T) {
	t.Parallel()

	ctx := context.Background()

	llm := provider.NewHTTPLLMProvider("", "")
	if _, err := llm.NotificationText(ctx, nil); !errors.Is(err, provider.ErrUnavailable) {
		t.Errorf("NotificationText err = %v, want ErrUnavailable", err)
	}
	if _, err := llm.SuggestActions(ctx, "x"); !errors.Is(err, provider.ErrUnavailable) {
		t.Errorf("SuggestActions err = %v, want ErrUnavailable", err)
	}
	if _, err := llm.Describe(ctx, "x"); !errors.Is(err, provider.ErrUnavailable) {
		t.Errorf("Describe err = %v, want ErrUnavailable", err)
	}
	if _, err := llm.ExtractActions(ctx, "x"); !errors.Is(err, provider.ErrUnavailable) {
		t.Errorf("ExtractActions err = %v, want ErrUnavailable", err)
	}

	stt := provider.NewHTTPTranscriptionProvider("", "")
	if _, err := stt.Transcribe(ctx, []byte("audio"), "audio/m4a"); !errors.Is(err, provider.ErrUnavailable) {
		t.Errorf("Transcribe err = %v, want ErrUnavailable", err)
	}
}
