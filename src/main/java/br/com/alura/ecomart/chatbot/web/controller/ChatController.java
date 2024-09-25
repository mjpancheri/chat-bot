package br.com.alura.ecomart.chatbot.web.controller;

import br.com.alura.ecomart.chatbot.domain.service.ChatbotService;
import br.com.alura.ecomart.chatbot.web.dto.PerguntaDto;
import com.theokanning.openai.completion.chat.ChatCompletionChunk;
import io.reactivex.Flowable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

@Controller
@RequestMapping({"/", "chat"})
public class ChatController {

    private static final String PAGINA_CHAT = "chat";
    private final ChatbotService service;

    public ChatController(ChatbotService service) {
        this.service = service;
    }

    @GetMapping
    public String carregarPaginaChatbot() {
        return PAGINA_CHAT;
    }

    @PostMapping
    @ResponseBody
    public String responderPergunta(@RequestBody PerguntaDto dto) {
        return service.responderPerguntaAssistant(dto.pergunta());
    }

    @PostMapping("v1")
    @ResponseBody
    public ResponseBodyEmitter responderPerguntaV1(@RequestBody PerguntaDto dto) {
        var fluxoResposta = service.responderPergunta(dto.pergunta());
        var emitter = new ResponseBodyEmitter();

        fluxoResposta.subscribe(
                chunk -> emitter.send(chunk.getChoices().get(0).getMessage().getContent()),
                emitter::completeWithError,
                emitter::complete
        );

        return emitter;
    }

    @GetMapping("limpar")
    public String limparConversa() {
        return PAGINA_CHAT;
    }

}
