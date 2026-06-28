package structs

type Tokenized struct{
	Mode string //MODE, CHAT, RS, FT, CONFIG, ANNOUNCE, DISTRIBUTE, ERROR
	Command string //applicable in ft and config
	Tokens []string //Message split by whitespace
	Payload string
	ErrorMessage string
}

type DeviceInfo struct{
	UUID string `json:"uuid"`
	DisplayName string `json:"display_name"`
}

type TrustedDevice struct{
	UUID string `json:"uuid"`
	DisplayName string `json:"display_name"`
	NickName string `json:"nickname"`
	PublicKey string `json:"public_key"`
}

type Config struct{
	DefaultMode string `json:"default_mode"`
}

type RuntimeData struct{
	IsStaged bool
	Mode string
	
}