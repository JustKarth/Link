package helpers

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"

	"link/internal/structs"
)

func SaveJSON(path string, data any) error {
	bytes, err := json.MarshalIndent(data, "", "    ")
	if err != nil {
		return err
	}

	return os.WriteFile(path, bytes, 0o644)
}

func LoadJSON(path string, target any) error {
	bytes, err := os.ReadFile(path)
	if err != nil {
		return err
	}

	return json.Unmarshal(bytes, target)
}

func ResolveDataPath(name string) (string, error) {
	dir, err := os.Getwd()
	if err != nil {
		return filepath.Join("data", name), nil
	}

	for {
		_, err := os.Stat(filepath.Join(dir, "go.mod"))
		if err == nil {
			return filepath.Join(dir, "data", name), nil
		}

		parent := filepath.Dir(dir)
		if parent == dir {
			return "", errors.New("project root not found")
		}
		dir = parent
	}
}

func SaveConfig(config *structs.Config) error {
	configPath, err := ResolveDataPath("config.json")
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(configPath), 0o755); err != nil {
		return err
	}

	return SaveJSON(configPath, config)
}
