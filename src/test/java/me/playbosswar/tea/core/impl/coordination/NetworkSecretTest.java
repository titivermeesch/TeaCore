package me.playbosswar.tea.core.impl.coordination;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

class NetworkSecretTest {

    @Test
    void generatesOnceAndReusesTheStoredSecret(@TempDir Path dir) throws Exception {
        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl("jdbc:sqlite:" + dir.resolve("tea.db"));
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE tea_network (name VARCHAR(64) NOT NULL PRIMARY KEY, value VARCHAR(255) NOT NULL)");
        }
        byte[] first = NetworkSecret.resolve(ds, "");
        byte[] second = NetworkSecret.resolve(ds, null);
        assertThat(first).isNotEmpty().isEqualTo(second);
        assertThat(NetworkSecret.resolve(ds, " mine ")).isEqualTo("mine".getBytes(StandardCharsets.UTF_8));
    }
}
