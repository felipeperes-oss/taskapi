package br.edu.ifsp.web.listener;

import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.util.concurrent.atomic.AtomicInteger;

@WebListener
public class SessoesListener implements HttpSessionListener {
    @Override
    public void sessionCreated(HttpSessionEvent evento) {
        int ativas = contador(evento).incrementAndGet();
        System.out.println("Sessão criada: " + evento.getSession().getId() + " | sessões ativas: " + ativas);
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent evento) {
        int ativas = contador(evento).decrementAndGet();
        System.out.println("Sessão destruída: " + evento.getSession().getId() + " | sessões ativas: " + ativas);
    }

    private AtomicInteger contador(HttpSessionEvent evento) {
        return (AtomicInteger) evento.getSession().getServletContext().getAttribute("sessoesAtivas");
    }
}
