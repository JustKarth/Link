package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"os"
	"strings"
	"link/internal/initialize"
	"link/internal/modules"
	"link/internal/parser"
	"link/internal/structs"
)

func loadConfig() (structs.Config, error) {
	var config structs.Config

	bytes, err := os.ReadFile("data/config.json")
	if err != nil {
		return config, err
	}

	err = json.Unmarshal(bytes, &config)
	if err != nil {
		return config, err
	}

	return config, nil
}

func main() {
	err := initialize.Run()
	if err != nil {
		fmt.Println(err)
		return
	}

	config, err := loadConfig()
	if err != nil {
		fmt.Println(err)
		return
	}

	runtime := structs.RuntimeData{Mode: config.DefaultMode}
	currentMode := runtime.Mode

	reader := bufio.NewReader(os.Stdin)

	for {
		fmt.Printf("[%s]> ", currentMode)

		rawInput, err := reader.ReadString('\n')
		if err != nil {
			fmt.Println(err)
			continue
		}

		rawInput = strings.TrimSpace(rawInput)
		if rawInput == "" {
			continue
		}

		tokenized := parser.Tokenize(rawInput, currentMode)

		if tokenized.Mode == "ERROR" {
			fmt.Println(tokenized.ErrorMessage)
			continue
		}

		switch tokenized.Mode {
		case "MODE":
			message, err := modules.ChangeMode(tokenized, &runtime)
			if err != nil {
				fmt.Println(err)
				continue
			}
			fmt.Println(message)
			currentMode = runtime.Mode
		case "CONFIG":
			fmt.Println("Config command received")
		case "CHAT":
			fmt.Println("Chat message sent")
		case "RS":
			fmt.Println("Remote shell input received")
		case "FT":
			fmt.Println("File transfer command received")
		case "ANNOUNCE":
			fmt.Println("Announcement command received")
		case "DISTRIBUTE":
			fmt.Println("Distribute command received")
		}
	}
}