package parser

import(
	"link/internal/structs"
	"strings"
)

func checkFTCommand(cmd string) bool{
	whitelist := []string{"ls", "cd", "pwd", "transferto", "transferfrom", "union", "intersect"}
	for _, item := range whitelist{
		if cmd==item{
			return true
		}
	}
	return false
}

func checkConfigCommand(cmd string) bool{
	whitelist := []string{"stage", "viewDevices", "connect", "unstage", "trustedDevices", "connectedDevices", "currentDevice", "disconnect", "mode"}
	for _, item := range whitelist{
		if cmd==item{
			return true
		}
	}
	return false
}

func Tokenize(msg string, mode string) structs.Tokenized{
	var input structs.Tokenized
	input.Tokens = strings.Fields(msg)

	if len(input.Tokens)==0{
		input.Mode = "ERROR"
		input.ErrorMessage = "Empty input"
		return input
	}

	if input.Tokens[0]=="/mode"{
		input.Mode = "MODE"
		return input
	}else if input.Tokens[0] == "/announce"{
		input.Mode = "ANNOUNCE"
		input.Payload = msg
		return input
	}else if input.Tokens[0] == "/distribute"{
		input.Mode = "DISTRIBUTE"
		input.Payload = msg
		return input
	}

	if mode=="CHAT"{
		input.Mode = "CHAT"
		input.Payload = msg
		return input
	}else if mode=="RS"{
		input.Mode = "RS"
		input.Payload = msg
		return input
	}else if mode=="FT"{
		if checkFTCommand(input.Tokens[0]){
			input.Mode = "FT"
			input.Command = input.Tokens[0]
			return input
		}else{
			input.Mode = "ERROR"
			input.ErrorMessage = "Invalid file transfer command."
			return input
		}
	}else if mode=="CONFIG"{
		if checkConfigCommand(input.Tokens[0]){
			input.Mode = "CONFIG"
			input.Command = input.Tokens[0]
			return input
		}else{
			input.Mode = "ERROR"
			input.ErrorMessage = "Invalid config command"
			return input
		}
	}
	input.Mode = "ERROR"
	input.ErrorMessage = "Mode not identified"
	return input
}