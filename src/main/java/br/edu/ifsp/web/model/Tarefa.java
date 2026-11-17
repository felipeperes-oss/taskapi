package br.edu.ifsp.web.model;

public final class Tarefa {
    private int id;
    private String titulo;
    private String descricao;
    private boolean concluida;
    private final Usuario usuario;

    public Tarefa(String titulo, String descricao, Usuario usuario) {
        this(0, titulo, descricao, usuario);
    }

    public Tarefa(int id, String titulo, String descricao, Usuario usuario) {
        if (usuario == null) {
            throw new IllegalArgumentException("O usuário da tarefa é obrigatório.");
        }
        this.id = id;
        setTitulo(titulo);
        this.descricao = descricao;
        this.usuario = usuario;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("O título da tarefa é obrigatório.");
        }
        this.titulo = titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public boolean isConcluida() {
        return concluida;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void concluir() {
        if (concluida) {
            throw new IllegalStateException("A tarefa já está concluída.");
        }
        concluida = true;
    }
}
