package net.openhft.chronicle.map;

import net.openhft.chronicle.map.serialization.BytesReader;
import net.openhft.chronicle.map.serialization.MetaBytesWriter;
import net.openhft.chronicle.map.serialization.MetaProvider;
import net.openhft.chronicle.map.serialization.SizeMarshaller;
import net.openhft.chronicle.map.threadlocal.Provider;
import net.openhft.chronicle.map.threadlocal.ThreadLocalCopies;
import net.openhft.lang.io.Bytes;
import net.openhft.lang.model.Byteable;
import net.openhft.lang.model.constraints.NotNull;

/**
 * uses a {@code SerializationBuilder} to read and write object(s) as bytes
 *
 * @author Rob Austin.
 */
public class Serializer<O, VW, MVW extends MetaBytesWriter<O, VW>> {

    final SizeMarshaller sizeMarshaller;
    final BytesReader<O> originalReader;
    transient Provider<BytesReader<O>> readerProvider;
    final VW originalWriter;
    transient Provider<VW> writerProvider;
    final MVW originalMetaValueWriter;
    final MetaProvider<O, VW, MVW> metaValueWriterProvider;

    public Serializer(final SerializationBuilder<O> serializationBuilder) {

        sizeMarshaller = serializationBuilder.sizeMarshaller();
        originalMetaValueWriter = (MVW) serializationBuilder.metaInterop();
        metaValueWriterProvider = (MetaProvider) serializationBuilder.metaInteropProvider();

        originalReader = serializationBuilder.reader();
        originalWriter = (VW) serializationBuilder.interop();

        readerProvider = Provider.of((Class) originalReader.getClass());
        writerProvider = Provider.of((Class) originalWriter.getClass());
    }

    public O readMarshallable(@NotNull Bytes in) throws IllegalStateException {

        final ThreadLocalCopies copies = readerProvider.getCopies(null);
        final BytesReader<O> valueReader = readerProvider.get(copies, originalReader);

        try {
            long valueSize = sizeMarshaller.readSize(in);
            return valueReader.read(in, valueSize, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void writeMarshallable(O value, @NotNull Bytes out) {

        ThreadLocalCopies copies = readerProvider.getCopies(null);
        VW valueWriter = writerProvider.get(copies, originalWriter);
        copies = writerProvider.getCopies(copies);

        final long valueSize;

        MetaBytesWriter<O, VW> metaValueWriter = null;

        if ((value instanceof Byteable)) {
            valueSize = ((Byteable) value).maxSize();
        } else {
            copies = writerProvider.getCopies(copies);
            valueWriter = writerProvider.get(copies, originalWriter);
            copies = metaValueWriterProvider.getCopies(copies);
            metaValueWriter = metaValueWriterProvider.get(
                    copies, originalMetaValueWriter, valueWriter, value);
            valueSize = metaValueWriter.size(valueWriter, value);
        }

        sizeMarshaller.writeSize(out, valueSize);

        if (metaValueWriter != null)
            metaValueWriter.write(valueWriter, out, value);
        else
            throw new UnsupportedOperationException("");

    }

}