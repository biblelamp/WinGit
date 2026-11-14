package cz.wingit.core.watch;

/**
 * A running watch of a working tree. Closing it stops notifications; it never throws.
 */
public interface RepositoryWatch extends AutoCloseable {

    @Override
    void close();
}
