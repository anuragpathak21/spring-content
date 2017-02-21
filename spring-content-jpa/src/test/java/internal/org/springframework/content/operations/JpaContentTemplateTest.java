package internal.org.springframework.content.operations;

import com.github.paulcwarren.ginkgo4j.Ginkgo4jConfiguration;
import com.github.paulcwarren.ginkgo4j.Ginkgo4jRunner;
import internal.org.springframework.content.jpa.operations.JpaContentTemplate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.springframework.content.commons.annotations.ContentId;
import org.springframework.content.commons.annotations.ContentLength;
import org.springframework.content.commons.utils.BeanUtils;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.sql.*;

import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.*;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.*;

@RunWith(Ginkgo4jRunner.class)
@Ginkgo4jConfiguration(threads=1) // required
public class JpaContentTemplateTest {

    private JpaContentTemplate template;

    // actors
    private TestEntity entity;
    private InputStream stream;

    // mocks
    private DataSource datasource;
    private Connection connection;
    private DatabaseMetaData metadata;
    private ResultSet resultSet;
    private PreparedStatement statement;
    private InputStream inputStream;

    private Blob blob;

    {
        Describe("JpaContentTemplate", () -> {
            Describe("#afterPropertiesSet", () -> {
                BeforeEach(() -> {
                    datasource = mock(DataSource.class);
                    connection = mock(Connection.class);
                    metadata = mock(DatabaseMetaData.class);
                    resultSet = mock(ResultSet.class);
                    statement = mock(PreparedStatement.class);
                });
                JustBeforeEach(() -> {
                    template = new JpaContentTemplate(datasource);
                    template.afterPropertiesSet();
                });
                Context("given the BLOBS table does not already exist", () -> {
                    BeforeEach(() -> {
                        when(datasource.getConnection()).thenReturn(connection);
                        when(connection.getMetaData()).thenReturn(metadata);
                        when(metadata.getTables(anyObject(), anyObject(), anyObject(), anyObject())).thenReturn(resultSet);
                        when(resultSet.next()).thenReturn(false);
                        when(connection.createStatement()).thenReturn(statement);
                    });
                    It("should execute sql CREATE TABLE statement", () -> {
                        verify(statement).executeUpdate(anyObject());
                    });
                    It("should close the resultset", () -> {
                        verify(resultSet).close();
                    });
                });
                Context("given the BLOBS table exists", () -> {
                    BeforeEach(() -> {
                        when(datasource.getConnection()).thenReturn(connection);
                        when(connection.getMetaData()).thenReturn(metadata);
                        when(metadata.getTables(anyObject(), anyObject(), anyObject(), anyObject())).thenReturn(resultSet);
                        when(resultSet.next()).thenReturn(true);
                    });
                    It("should not execute sql CREATE TABLE statement", () -> {
                        verify(statement, never()).executeUpdate(anyObject());
                    });
                    It("should close the resultset", () -> {
                        verify(resultSet).close();
                    });
                });
            });
            Describe("#setContent", () -> {
                BeforeEach(() -> {
                    datasource = mock(DataSource.class);
                    connection = mock(Connection.class);
                    statement = mock(PreparedStatement.class);
                    resultSet = mock(ResultSet.class);
                });
                JustBeforeEach(() -> {
                    template = new JpaContentTemplate(datasource);
                    template.setContent(entity, stream);
                });
                Context("given new content", () -> {
                    BeforeEach(() -> {
                        entity = new TestEntity();
                        stream = new ByteArrayInputStream("hello content world!".getBytes());
                    });
                    Context("given a generated key is returned for the new content", () -> {
                        BeforeEach(() -> {
                            when(datasource.getConnection()).thenReturn(connection);
                            when(connection.prepareStatement(anyObject(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn((PreparedStatement) statement);
                            when(statement.getGeneratedKeys()).thenReturn(resultSet);
                            when(resultSet.next()).thenReturn(true);
                            when(resultSet.getInt(eq("ID"))).thenReturn(12345);
                        });
                        It("inserts the content into the BLOBS table", () -> {
                            verify((PreparedStatement) statement).setBinaryStream(eq(1), isA(InputStream.class));
                        });
                        It("should close the resultset", () -> {
                            verify(resultSet).close();
                        });
                    });
                });
                Context("given content already exists", () -> {
                    BeforeEach(() -> {
                        entity = new TestEntity(12345);
                    });
                    Context("given a connection", () -> {
                        BeforeEach(() -> {
                            when(datasource.getConnection()).thenReturn(connection);
                            when(connection.prepareStatement(anyObject())).thenReturn((PreparedStatement) statement);
                        });
                        It("should UPDATE sql", () -> {
                            verify(connection).prepareStatement(startsWith("UPDATE BLOBS SET blob=?"));
                        });
                        It("should update the existing content", () -> {
                            verify((PreparedStatement) statement).setBinaryStream(eq(1), isA(InputStream.class));
                            verify((PreparedStatement) statement).executeUpdate();
                        });
                    });
                });
            });

            Describe("#unsetContent", () -> {
                BeforeEach(() -> {
                    datasource = mock(DataSource.class);
                    connection = mock(Connection.class);
                    statement = mock(PreparedStatement.class);
                });
                JustBeforeEach(() -> {
                    template = new JpaContentTemplate(datasource);
                    template.unsetContent(entity);
                });
                Context("given content to be deleted", () -> {
                    BeforeEach(() -> {
                        entity = new TestEntity();
                    });
                    Context("given a connection", () -> {
                        BeforeEach(() -> {
                            when(datasource.getConnection()).thenReturn(connection);
                            when(connection.prepareStatement(anyObject())).thenReturn((PreparedStatement) statement);
                        });
                        It("deletes the content with a DELETE statement", () -> {
                            verify(connection).prepareStatement(startsWith("DELETE FROM BLOBS WHERE id="));
                        });
                        It("should update the content id metadata", () -> {
                            assertThat(entity.getContentId(), is(nullValue()));
                        });
                        It("should update the content length metadata", () -> {
                            assertThat(entity.getContentLen(), is(0L));
                        });
                    });
                });
            });

            Describe("#getContent", () -> {
                BeforeEach(() -> {
                    datasource = mock(DataSource.class);
                    connection = mock(Connection.class);
                    statement = mock(PreparedStatement.class);
                    resultSet = mock(ResultSet.class);
                    blob = mock(Blob.class);
                });
                JustBeforeEach(() -> {
                    template = new JpaContentTemplate(datasource);
                    inputStream = template.getContent(entity);
                });
                Context("given content", () -> {
                    BeforeEach(() -> {
                        entity = new TestEntity(12345);
                        stream = new ByteArrayInputStream("hello content world!".getBytes());
                        when(datasource.getConnection()).thenReturn(connection);
                        when(connection.prepareStatement(anyObject())).thenReturn(statement);
                        when(statement.executeQuery()).thenReturn(resultSet);
                        when(resultSet.next()).thenReturn(true);
                        when(resultSet.getBlob(anyObject())).thenReturn(blob);
                        when(blob.getBinaryStream()).thenReturn(mock(InputStream.class));
                    });

                    It("should execute sql SELECT statement", () -> {
                        verify(connection).prepareStatement(eq("SELECT blob FROM BLOBS WHERE id='12345'"));
                    });

                    It("should return a content stream", () -> {
                        assertThat(inputStream, is(not(nullValue())));
                    });

                    It("should close the resultset", () -> {
                        verify(resultSet).close();
                    });
                });
            });
        });
    }

    @Test
    public void noop() {
    }

    public static class TestEntity {
        @ContentId
        private Integer contentId;
        @ContentLength
        private long contentLen;

        public TestEntity() {
            this.contentId = null;
        }

        public TestEntity(int contentId) {
            this.contentId = new Integer(contentId);
        }

        public Integer getContentId() {
            return this.contentId;
        }

        public void setContentId(Integer contentId) {
            this.contentId = contentId;
        }

        public long getContentLen() {
            return contentLen;
        }

        public void setContentLen(long contentLen) {
            this.contentLen = contentLen;
        }

    }
}