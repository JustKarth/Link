package modules

import (
	"fmt"
	"link/internal/structs"
	"strings"
)

func ChangeMode(tokenized structs.Tokenized, runtime *structs.RuntimeData) (string, error) {
	if runtime == nil {
		return "", fmt.Errorf("runtime data is nil")
	}

	if len(tokenized.Tokens) < 2 {
		return "", fmt.Errorf("mode requires a value")
	}

	modeValue := strings.ToUpper(tokenized.Tokens[1])
	if modeValue == "FILETRANSFER" || modeValue == "FT" {
		modeValue = "FT"
	} else if modeValue == "REMOTESHELL" || modeValue == "RS" {
		modeValue = "RS"
	} else if modeValue == "CONFIG" {
		modeValue = "CONFIG"
	} else if modeValue == "CHAT" {
		modeValue = "CHAT"
	} else {
		return "", fmt.Errorf("unsupported mode: %s", tokenized.Tokens[1])
	}

	runtime.Mode = modeValue

	return fmt.Sprintf("Mode updated to %s", runtime.Mode), nil
}
