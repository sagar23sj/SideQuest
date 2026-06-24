// Package games implements the server-authoritative pure logic for the two
// daily memory games: Spelling_Bee and Word_Guess.
//
// Everything here is deterministic and framework-free so the same Correctness
// Properties (21–25) validate it, and so all members of an organization receive
// an identical puzzle for a given date (Req 11.2). Puzzles are generated from a
// seed derived solely from (orgId, gameType, date); no wall-clock or random
// state leaks in. Scoring, completion, and the replay guard (Req 11.3, 11.4)
// are pure functions over an explicit game state.
package games

import (
	"crypto/sha256"
	"encoding/binary"
	"math/rand"
	"sort"
	"strings"

	"github.com/actiontracker/backend/internal/domain"
)

// LetterFeedback marks one position of a Word_Guess guess.
type LetterFeedback int

const (
	FeedbackUnknown LetterFeedback = iota // 0 = invalid/unset
	FeedbackCorrect                       // 1 = right letter, right position
	FeedbackPresent                       // 2 = right letter, wrong position
	FeedbackAbsent                        // 3 = letter not in remaining answer
)

// SpellingBeeSpec is the puzzle data for a Spelling_Bee: a set of allowed
// letters with one designated center letter that every accepted word must use.
type SpellingBeeSpec struct {
	Letters      []rune
	CenterLetter rune
}

// WordGuessSpec is the puzzle data for a Word_Guess: the hidden answer and the
// maximum number of attempts allowed.
type WordGuessSpec struct {
	Answer      string
	MaxAttempts int
}

// Puzzle is a generated daily puzzle for one org, game type, and date. Exactly
// one of the spec fields is populated, matching GameType.
type Puzzle struct {
	OrgID    string
	GameType domain.GameType
	Date     string // ISO calendar date, e.g. 2025-06-14
	Seed     uint64
	Bee      *SpellingBeeSpec
	Guess    *WordGuessSpec
}

// seedFor derives a deterministic 64-bit seed from (orgId, gameType, date).
// SHA-256 over a delimited key gives a stable, well-distributed seed that is
// identical on every call and on every server instance.
func seedFor(orgID string, gameType domain.GameType, date string) uint64 {
	h := sha256.New()
	// Length-delimited so distinct field boundaries can't collide
	// (e.g. "ab"+"c" vs "a"+"bc").
	_, _ = h.Write([]byte(orgID))
	_, _ = h.Write([]byte{0})
	_, _ = h.Write([]byte{byte(gameType)})
	_, _ = h.Write([]byte{0})
	_, _ = h.Write([]byte(date))
	sum := h.Sum(nil)
	return binary.BigEndian.Uint64(sum[:8])
}

// GeneratePuzzle builds the deterministic daily puzzle for the given org, game
// type, and date (Req 11.1, 11.2). The same inputs always yield an identical
// puzzle (Property 21). letterPool and wordList are the app's configured
// sources; they are passed in so the logic stays pure and testable.
func GeneratePuzzle(orgID string, gameType domain.GameType, date string, letterPool []rune, wordList []string) Puzzle {
	seed := seedFor(orgID, gameType, date)
	p := Puzzle{OrgID: orgID, GameType: gameType, Date: date, Seed: seed}

	// A fresh PRNG seeded deterministically. Selection below is order-stable.
	rng := rand.New(rand.NewSource(int64(seed))) //nolint:gosec // non-crypto, deterministic by design

	switch gameType {
	case domain.GameTypeSpellingBee:
		p.Bee = generateBee(rng, letterPool)
	case domain.GameTypeWordGuess:
		p.Guess = generateWordGuess(rng, wordList)
	}
	return p
}

// beeLetterCount is the number of distinct letters in a Spelling_Bee puzzle.
const beeLetterCount = 7

// generateBee deterministically selects the allowed letters and the center
// letter from the pool. It picks distinct letters by shuffling a copy of the
// pool with the seeded PRNG, then designates the first as the center letter.
func generateBee(rng *rand.Rand, letterPool []rune) *SpellingBeeSpec {
	pool := dedupeRunes(letterPool)
	if len(pool) == 0 {
		return &SpellingBeeSpec{}
	}

	// Stable shuffle of a copy (Fisher–Yates with the seeded PRNG).
	shuffled := make([]rune, len(pool))
	copy(shuffled, pool)
	for i := len(shuffled) - 1; i > 0; i-- {
		j := rng.Intn(i + 1)
		shuffled[i], shuffled[j] = shuffled[j], shuffled[i]
	}

	n := beeLetterCount
	if n > len(shuffled) {
		n = len(shuffled)
	}
	letters := shuffled[:n]

	return &SpellingBeeSpec{
		Letters:      letters,
		CenterLetter: letters[0],
	}
}

// generateWordGuess deterministically selects the answer word from the list.
func generateWordGuess(rng *rand.Rand, wordList []string) *WordGuessSpec {
	if len(wordList) == 0 {
		return &WordGuessSpec{MaxAttempts: defaultMaxAttempts}
	}
	idx := rng.Intn(len(wordList))
	return &WordGuessSpec{
		Answer:      strings.ToLower(wordList[idx]),
		MaxAttempts: defaultMaxAttempts,
	}
}

// defaultMaxAttempts is the standard Word_Guess attempt limit.
const defaultMaxAttempts = 6

// dedupeRunes returns the distinct runes of in, preserving first-seen order.
func dedupeRunes(in []rune) []rune {
	seen := make(map[rune]struct{}, len(in))
	out := make([]rune, 0, len(in))
	for _, r := range in {
		if _, ok := seen[r]; ok {
			continue
		}
		seen[r] = struct{}{}
		out = append(out, r)
	}
	return out
}

// EvaluateGuess returns per-position feedback for a Word_Guess guess against the
// answer (Req 11.5 / Property 24). It correctly handles duplicate letters using
// the standard two-pass algorithm:
//
//  1. Mark exact-position matches as Correct and consume those answer letters.
//  2. For each remaining guess position, mark Present if an unconsumed instance
//     of that letter remains in the answer, otherwise Absent.
//
// The returned slice has the same length as the guess. If the lengths differ,
// the comparison is done position-by-position up to the answer length and any
// extra guess positions are Absent.
func EvaluateGuess(answer, guess string) []LetterFeedback {
	a := []rune(strings.ToLower(answer))
	g := []rune(strings.ToLower(guess))

	feedback := make([]LetterFeedback, len(g))

	// remaining counts answer letters not yet consumed by a Correct match.
	remaining := make(map[rune]int, len(a))

	// Pass 1: exact matches.
	for i := range g {
		if i < len(a) && g[i] == a[i] {
			feedback[i] = FeedbackCorrect
		}
	}
	// Build the remaining pool from answer letters not matched exactly.
	for i := range a {
		if i < len(g) && g[i] == a[i] {
			continue // consumed by a Correct match
		}
		remaining[a[i]]++
	}

	// Pass 2: present/absent for non-correct positions.
	for i := range g {
		if feedback[i] == FeedbackCorrect {
			continue
		}
		if remaining[g[i]] > 0 {
			feedback[i] = FeedbackPresent
			remaining[g[i]]--
		} else {
			feedback[i] = FeedbackAbsent
		}
	}
	return feedback
}

// AcceptsWord reports whether a candidate word is valid for a Spelling_Bee
// puzzle (Req 11.6 / Property 25): every letter must be in the puzzle's allowed
// set AND the word must exist in the app word list. Comparison is
// case-insensitive. An empty candidate is never accepted.
func AcceptsWord(spec SpellingBeeSpec, wordList map[string]struct{}, candidate string) bool {
	word := strings.ToLower(strings.TrimSpace(candidate))
	if word == "" {
		return false
	}

	allowed := make(map[rune]struct{}, len(spec.Letters))
	for _, r := range spec.Letters {
		allowed[r] = struct{}{}
	}
	for _, r := range []rune(word) {
		if _, ok := allowed[r]; !ok {
			return false
		}
	}

	_, inList := wordList[word]
	return inList
}

// SortedLetters returns the puzzle's allowed letters sorted, useful for stable
// display and for tests asserting determinism without depending on selection
// order.
func SortedLetters(spec SpellingBeeSpec) []rune {
	out := make([]rune, len(spec.Letters))
	copy(out, spec.Letters)
	sort.Slice(out, func(i, j int) bool { return out[i] < out[j] })
	return out
}
