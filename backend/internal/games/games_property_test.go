package games

import (
	"math/rand"
	"reflect"
	"strings"
	"testing"
	"testing/quick"

	"github.com/actiontracker/backend/internal/domain"
)

// minIterations is the required minimum number of generated cases per property.
const minIterations = 100

// sampleLetterPool and sampleWordList back the generators. They are fixed so
// puzzle generation is fully determined by the seed.
var sampleLetterPool = []rune("abcdefghijklmnopqrstuvwxyz")

var sampleWordList = []string{
	"apple", "table", "cabin", "lemon", "melon", "plane", "crane",
	"beach", "ocean", "tiger", "zebra", "mango", "grape", "peach",
}

// Feature: action-tracker-app, Property 21: Daily puzzles are deterministic per organization and date
//
// Property 21 (Req 11.2): for any organization, game type, and date, generating
// the daily puzzle yields an identical puzzle on every generation, so all users
// in the same org on the same day receive the same puzzle.
func TestGeneratePuzzle_Property21(t *testing.T) {
	t.Parallel()

	cfg := &quick.Config{MaxCount: minIterations}

	deterministic := func(orgID, date string, gtSel uint8) bool {
		gt := pickGameType(gtSel)

		first := GeneratePuzzle(orgID, gt, date, sampleLetterPool, sampleWordList)
		second := GeneratePuzzle(orgID, gt, date, sampleLetterPool, sampleWordList)

		// Identical seed and identical spec on every generation.
		return reflect.DeepEqual(first, second)
	}

	if err := quick.Check(deterministic, cfg); err != nil {
		t.Errorf("puzzle generation is not deterministic: %v", err)
	}

	t.Run("different date or org yields a different seed sometimes", func(t *testing.T) {
		t.Parallel()
		// Sanity: the seed actually varies with inputs (not a constant).
		a := seedFor("org-a", domain.GameTypeWordGuess, "2025-06-14")
		b := seedFor("org-b", domain.GameTypeWordGuess, "2025-06-14")
		c := seedFor("org-a", domain.GameTypeWordGuess, "2025-06-15")
		if a == b && a == c {
			t.Errorf("seed does not vary with org/date: %d", a)
		}
	})
}

// Feature: action-tracker-app, Property 24: Word_Guess feedback is correct, including duplicate letters
//
// Property 24 (Req 11.5): for any answer and guess of equal length, the feedback
// has the same length as the guess; every position whose letter equals the
// answer's letter is marked correct; present markings never exceed the count of
// remaining (non-correct-matched) occurrences of that letter in the answer; and
// a guess equal to the answer yields all correct.
func TestEvaluateGuess_Property24(t *testing.T) {
	t.Parallel()

	cfg := &quick.Config{MaxCount: minIterations}

	t.Run("feedback respects correctness and present bounds", func(t *testing.T) {
		t.Parallel()

		check := func(p wordPair) bool {
			answer, guess := p.Answer, p.Guess
			fb := EvaluateGuess(answer, guess)

			a := []rune(strings.ToLower(answer))
			g := []rune(strings.ToLower(guess))

			// Same length as the guess.
			if len(fb) != len(g) {
				return false
			}

			// Every exact-position match is Correct, and only those positions.
			for i := range g {
				exact := i < len(a) && g[i] == a[i]
				if exact && fb[i] != FeedbackCorrect {
					return false
				}
				if !exact && fb[i] == FeedbackCorrect {
					return false
				}
			}

			// Present markings per letter must not exceed remaining (non-
			// correct-matched) occurrences of that letter in the answer.
			remaining := map[rune]int{}
			for i := range a {
				if i < len(g) && g[i] == a[i] {
					continue
				}
				remaining[a[i]]++
			}
			presentCount := map[rune]int{}
			for i := range g {
				if fb[i] == FeedbackPresent {
					presentCount[g[i]]++
				}
			}
			for r, c := range presentCount {
				if c > remaining[r] {
					return false
				}
			}
			return true
		}

		if err := quick.Check(check, cfg); err != nil {
			t.Errorf("Word_Guess feedback is incorrect: %v", err)
		}
	})

	t.Run("guess equal to answer is all correct", func(t *testing.T) {
		t.Parallel()

		allCorrect := func(word string) bool {
			w := strings.ToLower(word)
			if w == "" {
				return true // nothing to check
			}
			fb := EvaluateGuess(w, w)
			for _, f := range fb {
				if f != FeedbackCorrect {
					return false
				}
			}
			return true
		}

		if err := quick.Check(allCorrect, cfg); err != nil {
			t.Errorf("identical guess is not all-correct: %v", err)
		}
	})

	t.Run("duplicate-letter example", func(t *testing.T) {
		t.Parallel()
		// answer "abbey", guess "babee":
		//  pos0 b vs a -> not correct
		//  pos1 a vs b -> not correct
		//  pos2 b vs b -> correct
		//  pos3 e vs e -> correct
		//  pos4 e vs y -> not correct
		// remaining (after correct): a:1, b:1, y:1
		//  pos0 b -> present (b remaining 1 -> 0)
		//  pos1 a -> present (a remaining 1 -> 0)
		//  pos4 e -> absent (no e remaining)
		got := EvaluateGuess("abbey", "babee")
		want := []LetterFeedback{
			FeedbackPresent, FeedbackPresent, FeedbackCorrect, FeedbackCorrect, FeedbackAbsent,
		}
		if !reflect.DeepEqual(got, want) {
			t.Errorf("EvaluateGuess(abbey, babee) = %v, want %v", got, want)
		}
	})
}

// Feature: action-tracker-app, Property 25: Spelling_Bee acceptance follows the allowed-letters and word-list rule
//
// Property 25 (Req 11.6): for any candidate word, it is accepted if and only if
// all of its letters are in the puzzle's allowed letter set and the word exists
// in the app word list.
func TestAcceptsWord_Property25(t *testing.T) {
	t.Parallel()

	cfg := &quick.Config{MaxCount: minIterations}

	// A fixed puzzle and word list to check the biconditional against.
	spec := SpellingBeeSpec{Letters: []rune("ablet"), CenterLetter: 'a'}
	allowed := map[rune]struct{}{}
	for _, r := range spec.Letters {
		allowed[r] = struct{}{}
	}
	wordList := map[string]struct{}{
		"table": {}, "able": {}, "bleat": {}, "apple": {}, "lemon": {},
	}

	iff := func(raw string) bool {
		candidate := strings.ToLower(strings.TrimSpace(raw))

		got := AcceptsWord(spec, wordList, raw)

		// Independent reference computation of the rule.
		_, inList := wordList[candidate]
		allLettersAllowed := candidate != ""
		for _, r := range []rune(candidate) {
			if _, ok := allowed[r]; !ok {
				allLettersAllowed = false
				break
			}
		}
		want := inList && allLettersAllowed

		return got == want
	}

	if err := quick.Check(iff, cfg); err != nil {
		t.Errorf("Spelling_Bee acceptance violates the biconditional: %v", err)
	}

	t.Run("examples", func(t *testing.T) {
		t.Parallel()
		cases := []struct {
			word string
			want bool
		}{
			{"table", true},   // in list, letters allowed
			{"able", true},    // in list, letters allowed
			{"apple", false},  // in list but 'p' not allowed
			{"lemon", false},  // in list but letters not allowed
			{"bleat", true},   // in list, letters allowed
			{"tablet", false}, // letters allowed but not in list
			{"", false},       // empty never accepted
		}
		for _, c := range cases {
			if got := AcceptsWord(spec, wordList, c.word); got != c.want {
				t.Errorf("AcceptsWord(%q) = %v, want %v", c.word, got, c.want)
			}
		}
	})
}

// pickGameType maps an arbitrary byte to one of the two real game types so
// generators exercise both.
func pickGameType(sel uint8) domain.GameType {
	if sel%2 == 0 {
		return domain.GameTypeSpellingBee
	}
	return domain.GameTypeWordGuess
}

// wordPair is an (answer, guess) pair of equal-ish length drawn from a small
// alphabet so duplicate letters occur often. It implements quick.Generator.
type wordPair struct {
	Answer string
	Guess  string
}

// Generate produces an answer and a guess of the same length over a tiny
// alphabet {a,b,e,y} to maximize duplicate-letter coverage.
func (wordPair) Generate(rnd *rand.Rand, _ int) reflect.Value {
	const alphabet = "abey"
	n := 1 + rnd.Intn(6)
	mk := func() string {
		b := make([]byte, n)
		for i := range b {
			b[i] = alphabet[rnd.Intn(len(alphabet))]
		}
		return string(b)
	}
	return reflect.ValueOf(wordPair{Answer: mk(), Guess: mk()})
}
