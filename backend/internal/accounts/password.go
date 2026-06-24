package accounts

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"errors"
	"fmt"
	"strings"

	"golang.org/x/crypto/argon2"
)

// Argon2id parameters. These follow the golang-security guidance (memory-hard
// password hashing, never plaintext, never MD5/SHA1). The values are tuned for
// interactive logins; a DBA/security review may adjust them for the target
// hardware. They are encoded into every hash so existing hashes remain
// verifiable after a parameter change.
const (
	argon2Time      = 3         // iterations
	argon2Memory    = 64 * 1024 // 64 MiB
	argon2Threads   = 4
	argon2KeyLength = 32
	argon2SaltBytes = 16
)

// argon2idPrefix tags the encoded hash with its algorithm so future migrations
// can detect the scheme.
const argon2idPrefix = "argon2id"

// ErrInvalidHash is returned when a stored password hash cannot be parsed.
var ErrInvalidHash = errors.New("accounts: invalid password hash")

// hashPassword derives an argon2id hash of password using a fresh random salt
// and returns a self-describing encoded string of the form:
//
//	argon2id$v=19$m=65536,t=3,p=4$<base64-salt>$<base64-hash>
//
// The encoding embeds the parameters and salt so verifyPassword needs no
// external state. The plaintext password is never stored or logged.
func hashPassword(password string) (string, error) {
	salt := make([]byte, argon2SaltBytes)
	if _, err := rand.Read(salt); err != nil {
		return "", fmt.Errorf("accounts: generating salt: %w", err)
	}

	key := argon2.IDKey([]byte(password), salt, argon2Time, argon2Memory, argon2Threads, argon2KeyLength)

	encoded := fmt.Sprintf("%s$v=%d$m=%d,t=%d,p=%d$%s$%s",
		argon2idPrefix,
		argon2.Version,
		argon2Memory,
		argon2Time,
		argon2Threads,
		base64.RawStdEncoding.EncodeToString(salt),
		base64.RawStdEncoding.EncodeToString(key),
	)
	return encoded, nil
}

// verifyPassword reports whether password matches the previously encoded hash.
// The comparison is constant-time (crypto/subtle) to avoid leaking timing
// information about how many leading bytes matched.
func verifyPassword(password, encoded string) (bool, error) {
	params, salt, want, err := decodeHash(encoded)
	if err != nil {
		return false, err
	}

	got := argon2.IDKey([]byte(password), salt, params.time, params.memory, params.threads, uint32(len(want)))

	if subtle.ConstantTimeCompare(got, want) == 1 {
		return true, nil
	}
	return false, nil
}

// argon2Params holds the cost parameters parsed from an encoded hash.
type argon2Params struct {
	memory  uint32
	time    uint32
	threads uint8
}

// decodeHash parses an encoded argon2id hash back into its parameters, salt,
// and derived key. It validates the structure strictly so a malformed stored
// value surfaces as ErrInvalidHash rather than a silent mismatch.
func decodeHash(encoded string) (argon2Params, []byte, []byte, error) {
	var p argon2Params

	parts := strings.Split(encoded, "$")
	if len(parts) != 5 || parts[0] != argon2idPrefix {
		return p, nil, nil, ErrInvalidHash
	}

	var version int
	if _, err := fmt.Sscanf(parts[1], "v=%d", &version); err != nil {
		return p, nil, nil, fmt.Errorf("%w: version: %v", ErrInvalidHash, err)
	}
	if version != argon2.Version {
		return p, nil, nil, fmt.Errorf("%w: unsupported version %d", ErrInvalidHash, version)
	}

	if _, err := fmt.Sscanf(parts[2], "m=%d,t=%d,p=%d", &p.memory, &p.time, &p.threads); err != nil {
		return p, nil, nil, fmt.Errorf("%w: params: %v", ErrInvalidHash, err)
	}

	salt, err := base64.RawStdEncoding.DecodeString(parts[3])
	if err != nil {
		return p, nil, nil, fmt.Errorf("%w: salt: %v", ErrInvalidHash, err)
	}

	want, err := base64.RawStdEncoding.DecodeString(parts[4])
	if err != nil {
		return p, nil, nil, fmt.Errorf("%w: key: %v", ErrInvalidHash, err)
	}

	return p, salt, want, nil
}
