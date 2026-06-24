package accounts

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
)

// newUUID returns a random RFC 4122 version 4 UUID string. It uses crypto/rand
// (never math/rand) so ids are unpredictable. The accounts and organizations
// tables use UUID primary keys; account/org ids are server-generated here.
func newUUID() (string, error) {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", fmt.Errorf("accounts: generating uuid: %w", err)
	}
	// Set version (4) and variant (RFC 4122) bits.
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80

	var dst [36]byte
	hex.Encode(dst[0:8], b[0:4])
	dst[8] = '-'
	hex.Encode(dst[9:13], b[4:6])
	dst[13] = '-'
	hex.Encode(dst[14:18], b[6:8])
	dst[18] = '-'
	hex.Encode(dst[19:23], b[8:10])
	dst[23] = '-'
	hex.Encode(dst[24:36], b[10:16])
	return string(dst[:]), nil
}
