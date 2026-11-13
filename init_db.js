const mysql = require('mysql2/promise');
const fs = require('fs');
const path = require('path');

async function initDatabase() {
    const sqlFile = path.join(__dirname, 'stray-cat-server/sql/init.sql');
    const sql = fs.readFileSync(sqlFile, 'utf-8');

    console.log('Connecting to MySQL at 192.168.156.132...');

    const connection = await mysql.createConnection({
        host: '192.168.156.132',
        port: 3306,
        user: 'root',
        password: '123',
        multipleStatements: true
    });

    try {
        console.log('Connected! Executing SQL script...');
        const statements = sql.split(';').filter(s => s.trim().length > 0);

        for (const stmt of statements) {
            const trimmed = stmt.trim();
            if (trimmed) {
                await connection.query(trimmed + ';');
                // 提取表名用于日志
                const match = trimmed.match(/(?:CREATE TABLE IF NOT EXISTS|INSERT INTO)\s+(\w+)/i);
                if (match) {
                    console.log(`  ✓ ${match[1]}`);
                }
            }
        }

        console.log('\nDatabase initialized successfully!');

        // 验证
        const [rows] = await connection.query('SHOW TABLES FROM stray_cat');
        console.log(`\nTables created: ${rows.length}`);
        rows.forEach(r => console.log(`  - ${Object.values(r)[0]}`));

    } catch (err) {
        console.error('Error:', err.message);
    } finally {
        await connection.end();
    }
}

initDatabase();
