package com.link.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.link.networking.SecureSession;
import com.link.transfer.models.ControlMessage;

public class TransferProtocolManager {
    private final ObjectMapper mapper = new ObjectMapper();

    public void sendMessage(SecureSession session, ControlMessage message) throws Exception {
        session.sendMessage(mapper.writeValueAsString(message));
    }

    public ControlMessage receiveMessage(SecureSession session) throws Exception {
        String json = session.receiveMessage();
        return mapper.readValue(json, ControlMessage.class);
    }
}