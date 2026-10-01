app.get('/api/sugestoes', async (req, res) => {
    const termo = req.query.q;

    // Se não digitou nada, retorna vazio
    if (!termo || termo.length < 2) {
        return res.json([]);
    }

    try {
        const query = `
            SELECT titulo 
            FROM musicas 
            WHERE titulo LIKE ? 
            LIMIT 10
        `;

        const [rows] = await db.execute(query, [`%${termo}%`]);

        const sugestoes = rows.map(row => row.titulo);

        // Devolve para o frontend
        res.json(sugestoes);

    } catch (error) {
        console.error(error);
        res.status(500).json({ error: "Erro no servidor" });
    }
});