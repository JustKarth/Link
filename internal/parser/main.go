package parser

import(
	"link/internal/structs"
)

func parse(msg string) structs.Input{
	var input structs.Input
	if msg[0]=='/'{
		input.Type = "command"
		
	}else{
		input.Type = ""
	}
}