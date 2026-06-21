package com.kk2004.kmessage.api;

import com.kk2004.common.response.TransDTO;
import com.kk2004.kmessage.message.MessageService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final MessageService messages;
    public MessageController(MessageService messages) { this.messages = messages; }

    @PostMapping
    public ResponseEntity<TransDTO<Object>> submit(@RequestBody MessageService.SubmitRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(TransDTO.success(messages.submit(request)));
    }
    @GetMapping("/{id}")
    public TransDTO<MessageService.MessageDetail> get(@PathVariable String id) {
        return TransDTO.success(messages.get(id));
    }
}
