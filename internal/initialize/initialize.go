package initialize

import (
	"bufio"
	"bytes"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"fmt"
	"os"
	"strings"

	"github.com/google/uuid"
	"link/internal/helpers"
	"link/internal/structs"
)

func fileExists(path string) bool {
	_, err := os.Stat(path)

	return err == nil
}

func ensureDataDir() error {
	return os.MkdirAll("data", 0755)
}

func askDisplayName() (string, error) {
	hostname, err := os.Hostname()
	if err != nil {
		return "", err
	}

	fmt.Printf("Use '%s' as display name? [Y/n]: ",	hostname)

	reader := bufio.NewReader(os.Stdin)

	answer, err := reader.ReadString('\n')
	if err != nil {
		return "", err
	}

	answer = strings.TrimSpace(answer)
	if answer == "" || answer == "Y" ||	answer == "y"{
		return hostname, nil
	}

	for{
		fmt.Print("Enter display name: ")

		displayName, err :=	reader.ReadString('\n')
		if err != nil {
			return "", err
		}

		displayName = strings.TrimSpace(displayName)

		if displayName == "" {
			fmt.Println("Display name cannot be empty")
			continue
		}

		return displayName, nil
	}
}

func validateDeviceInfo(device structs.DeviceInfo) error{
	if strings.TrimSpace(device.UUID) == "" {
		return errors.New("missing uuid")
	}

	if strings.TrimSpace(device.DisplayName) == "" {
		return errors.New(
			"missing display name",
		)
	}

	return nil
}

func ensureDeviceInfo() error {
	if fileExists("data/deviceInfo.json") {
		var device structs.DeviceInfo
		err := helpers.LoadJSON(
			"data/deviceInfo.json",
			&device,
		)

		if err == nil {
			err = validateDeviceInfo(device)
			if err == nil {
				return nil
			}
		}
		fmt.Println("deviceInfo.json invalid, recreating")
	}

	displayName, err :=	askDisplayName()
	if err != nil {
		return err
	}

	device := structs.DeviceInfo{
		UUID: uuid.New().String(),
		DisplayName: displayName,
	}

	return helpers.SaveJSON("data/deviceInfo.json", device)
}

func validateTrustedDevices(devices []structs.TrustedDevice) error{
	for _, device := range devices{
		if strings.TrimSpace(device.UUID) == ""{
			return errors.New("device missing uuid")
		}

		if strings.TrimSpace(device.PublicKey) == "" {
			return errors.New("device missing public key")
		}
	}

	return nil
}

func ensureTrustedDevices() error {
	if fileExists("data/trustedDevices.json") {
		var devices []structs.TrustedDevice

		err := helpers.LoadJSON("data/trustedDevices.json", &devices)
		if err == nil {
			err = validateTrustedDevices(devices)
			if err == nil {
				return nil
			}
		}

		fmt.Println("trustedDevices.json invalid, recreating")
	}

	devices := []structs.TrustedDevice{}

	return helpers.SaveJSON("data/trustedDevices.json", devices)
}

func validateConfig(config structs.Config) error{
	switch config.DefaultMode {
	case "CHAT", "FT", "RS", "CONFIG": 
		return nil
	default:
		return errors.New("invalid mode")
	}
}

func ensureConfig() error {
	if fileExists("data/config.json") {
		var config structs.Config
		err := helpers.LoadJSON("data/config.json", &config)
		if err == nil {
			err = validateConfig(config)
			if err == nil {
				return nil
			}
		}

		fmt.Println("config.json invalid, recreating")
	}

	config := structs.Config{
		DefaultMode: "CHAT",
	}

	return helpers.SaveJSON("data/config.json", config)
}

func validateKeys() error {
	privateBytes, err := os.ReadFile("data/private.key")
	if err != nil {
		return err
	}

	publicBytes, err := os.ReadFile("data/public.key")
	if err != nil {
		return err
	}

	privateKey, err := base64.StdEncoding.DecodeString(string(privateBytes))
	if err != nil {
		return err
	}

	publicKey, err := base64.StdEncoding.DecodeString(string(publicBytes))
	if err != nil {
		return err
	}

	if len(privateKey) != ed25519.PrivateKeySize {
		return errors.New("invalid private key")
	}

	if len(publicKey) != ed25519.PublicKeySize {
		return errors.New("invalid public key")
	}

	derivedPublic := ed25519.PrivateKey(privateKey).Public().(ed25519.PublicKey)

	if !bytes.Equal(derivedPublic, publicKey) {
		return errors.New("public and private key do not match")
	}

	return nil
}

func ensureKeys() error {
	privateExists := fileExists("data/private.key")
	publicExists := fileExists("data/public.key")

	if privateExists && publicExists {
		err := validateKeys()
		if err == nil {
			return nil
		}

		fmt.Println("keys invalid, regenerating")
	}

	if privateExists != publicExists {
		fmt.Println("incomplete keypair found, regenerating")
	}

	publicKey, privateKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		return err
	}

	encodedPrivate := base64.StdEncoding.EncodeToString(privateKey)
	encodedPublic := base64.StdEncoding.EncodeToString(publicKey)

	err = os.WriteFile("data/private.key", []byte(encodedPrivate), 0600)
	if err != nil {
		return err
	}

	err = os.WriteFile("data/public.key", []byte(encodedPublic), 0644)
	if err != nil {
		return err
	}

	return nil
}

func Run() error {
	err := ensureDataDir()
	if err != nil {
		return err
	}

	err = ensureDeviceInfo()
	if err != nil {
		return err
	}

	err = ensureKeys()
	if err != nil {
		return err
	}

	err = ensureTrustedDevices()
	if err != nil {
		return err
	}

	err = ensureConfig()
	if err != nil {
		return err
	}

	return nil
}