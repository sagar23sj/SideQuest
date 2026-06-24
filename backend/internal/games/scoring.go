package games

import (
	"strings"

	"github.com/actiontracker/backend/internal/domain"
)

// WordGuessState is the evolving state of a Word_Guess play: the guesses made
// so far against a fixed puzzle.
type WordGuessState struct {
	Spec    WordGuessSpec
	Guesses []string
}

// solved reports whether any guess exactly matches the answer.
func (s WordGuessState) solved() bool {
	answer := strings.ToLower(s.Spec.Answer)
	for _, g := range s.Guesses {
		if strings.ToLower(g) == answer {
			return true
		}
	}
	return false
}

// IsComplete reports whether a Word_Guess play has ended: either the answer was
// found or the attempt limit was reached.
func (s WordGuessState) IsComplete() bool {
	return s.solved() || len(s.Guesses) >= s.Spec.MaxAttempts
}

// Score computes the Word_Guess score from the final state (Req 11.3). A solved
// puzzle scores more the fewer attempts it took; an unsolved puzzle scores
// zero. The scoring is a pure function of the final state so recording is
// idempotent given the same state.
func (s WordGuessState) Score() int {
	if !s.solved() {
		return 0
	}
	// attempts is the number of guesses up to and including the winning one.
	attempts := s.winningAttempt()
	// Reward speed: max points for a first-try solve, decreasing by attempt.
	const pointsPerSavedAttempt = 10
	saved := s.Spec.MaxAttempts - attempts // 0..MaxAttempts-1
	if saved < 0 {
		saved = 0
	}
	return (saved + 1) * pointsPerSavedAttempt
}

// winningAttempt returns the 1-based index of the first correct guess, or
// len(Guesses) if none (shouldn't happen when solved()).
func (s WordGuessState) winningAttempt() int {
	answer := strings.ToLower(s.Spec.Answer)
	for i, g := range s.Guesses {
		if strings.ToLower(g) == answer {
			return i + 1
		}
	}
	return len(s.Guesses)
}

// SpellingBeeState is the evolving state of a Spelling_Bee play: the set of
// accepted words found so far.
type SpellingBeeState struct {
	Spec        SpellingBeeSpec
	FoundWords  []string
	TargetWords int // number of valid words needed to "complete" the puzzle
}

// IsComplete reports whether enough words have been found to end the play. When
// TargetWords is zero the puzzle is considered open-ended and never auto-
// completes; the client ends it explicitly.
func (s SpellingBeeState) IsComplete() bool {
	return s.TargetWords > 0 && len(s.FoundWords) >= s.TargetWords
}

// Score computes the Spelling_Bee score from the final state (Req 11.3): one
// point per letter across all found words, with a bonus for pangrams (words
// using every allowed letter). Pure over the final state.
func (s SpellingBeeState) Score() int {
	const pangramBonus = 7
	allowed := make(map[rune]struct{}, len(s.Spec.Letters))
	for _, r := range s.Spec.Letters {
		allowed[r] = struct{}{}
	}

	total := 0
	for _, w := range s.FoundWords {
		runes := []rune(strings.ToLower(w))
		total += len(runes)
		if isPangram(runes, allowed) {
			total += pangramBonus
		}
	}
	return total
}

// isPangram reports whether word uses every allowed letter at least once.
func isPangram(word []rune, allowed map[rune]struct{}) bool {
	used := make(map[rune]struct{}, len(allowed))
	for _, r := range word {
		if _, ok := allowed[r]; ok {
			used[r] = struct{}{}
		}
	}
	return len(used) == len(allowed)
}

// RecordScore applies the replay guard (Req 11.4 / Properties 22, 23). Given the
// already-recorded result for a (account, game, date) — if any — and a freshly
// completed play's score, it returns the result that should be persisted and
// whether a new score was recorded.
//
//   - If a result already exists for that day, it is returned unchanged and
//     recorded == false (no rescoring on replay).
//   - Otherwise a new GameResult is produced from the final score and
//     recorded == true (exactly one score recorded on completion).
func RecordScore(existing *domain.GameResult, newResult domain.GameResult) (result domain.GameResult, recorded bool) {
	if existing != nil {
		return *existing, false
	}
	return newResult, true
}
