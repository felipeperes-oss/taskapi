USE taskdb;

CREATE TABLE usuarios (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

INSERT INTO usuarios (nome, email, senha_hash) VALUES
    ('Ana', 'ana@taskapi.test', 'pbkdf2-sha256$600000$yR8Yd1zxZBr7eT5F1ls/gw==$9q/6YlpM3CiPY6WcoOWESVAgnCbe2nGAVb4OdSw9PhQ='),
    ('Bruno', 'bruno@taskapi.test', 'pbkdf2-sha256$600000$hakdBcrz2nokxyqfbHaq+Q==$NVAFbovhuxdidwkT3ITrIp6XXeT2w+KXF2A25uSD03Y=');

SET @usuario_ana = (SELECT id FROM usuarios WHERE email = 'ana@taskapi.test');
SET @usuario_bruno = (SELECT id FROM usuarios WHERE email = 'bruno@taskapi.test');

ALTER TABLE tarefas ENGINE=InnoDB, ADD COLUMN usuario_id INT NULL;

UPDATE tarefas SET usuario_id = @usuario_ana;

ALTER TABLE tarefas
    MODIFY COLUMN usuario_id INT NOT NULL,
    ADD CONSTRAINT fk_tarefas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id);

INSERT INTO tarefas (titulo, descricao, concluida, usuario_id) VALUES
    ('Revisar sessoes', 'Revisar o material de sessoes e cookies', FALSE, @usuario_ana),
    ('Estudar Service Layer', 'Ler o material sobre camadas', FALSE, @usuario_ana),
    ('Preparar ambiente', 'Instalar as ferramentas da atividade', TRUE, @usuario_ana),
    ('Revisar comandos', 'Revisar o Front Controller e os Commands', FALSE, @usuario_bruno),
    ('Organizar estudos', 'Separar os materiais das aulas', TRUE, @usuario_bruno),
    ('Planejar verificacoes', 'Preparar os cenarios de teste da API', FALSE, @usuario_bruno);
