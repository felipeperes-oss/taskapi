package br.edu.ifsp.web.listener;

import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import java.util.concurrent.atomic.AtomicInteger;

@WebListener
public class SessoesListener implements HttpSessionListener {
    @Override
    public void sessionCreated(HttpSessionEvent evento) {
        contador(evento).incrementAndGet();
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent evento) {
        contador(evento).decrementAndGet();
    }

    private AtomicInteger contador(HttpSessionEvent evento) {
        return (AtomicInteger) evento.getSession().getServletContext().getAttribute("sessoesAtivas");
    }
}
