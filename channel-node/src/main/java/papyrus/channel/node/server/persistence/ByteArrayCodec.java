package papyrus.channel.node.server.persistence;

import java.nio.ByteBuffer;

import org.web3j.utils.Numeric;

import com.datastax.driver.core.DataType;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.TypeCodec;

public class ByteArrayCodec extends TypeCodec<byte[]> {

    public ByteArrayCodec() {
        super(DataType.blob(), byte[].class);
    }

    @Override
    public byte[] parse(String value) {
        return value == null || value.isEmpty() || value.equalsIgnoreCase("NULL") ? null : Numeric.hexStringToByteArray(value);
    }

    @Override
    public String format(byte[] value) {
        if (value == null)
            return "NULL";
        return Numeric.toHexString(value);
    }

    @Override
    public ByteBuffer serialize(byte[] value, ProtocolVersion protocolVersion) {
        return value == null ? null : ByteBuffer.wrap(value.clone());
    }

    @Override
    public byte[] deserialize(ByteBuffer bytes, ProtocolVersion protocolVersion) {
        return bytes == null ? null : bytes.array().clone();
    }
}
