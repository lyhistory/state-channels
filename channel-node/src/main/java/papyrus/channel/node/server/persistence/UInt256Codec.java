package papyrus.channel.node.server.persistence;

import java.nio.ByteBuffer;

import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.utils.Numeric;

import com.datastax.driver.core.DataType;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.TypeCodec;
import com.datastax.driver.core.exceptions.InvalidTypeException;

public class UInt256Codec extends TypeCodec<Uint256> {

    private static final int BYTES = 32;
    private static final int CHARS = BYTES * 2;

    public UInt256Codec() {
        super(DataType.blob(), Uint256.class);
    }

    @Override
    public ByteBuffer serialize(Uint256 value, ProtocolVersion protocolVersion) throws InvalidTypeException {
        if (value == null) return null;
        byte[] bytes = Numeric.toBytesPadded(value.getValue(), BYTES);
        return ByteBuffer.wrap(bytes);
    }

    @Override
    public Uint256 deserialize(ByteBuffer bytes, ProtocolVersion protocolVersion) throws InvalidTypeException {
        if (bytes == null) return null;
        return new Uint256(Numeric.toBigInt(bytes.array(), 0, BYTES));
    }

    @Override
    public Uint256 parse(String value) throws InvalidTypeException {
        return new Uint256(Numeric.toBigInt(value));
    }

    @Override
    public String format(Uint256 value) throws InvalidTypeException {
        return Numeric.toHexStringNoPrefixZeroPadded(value.getValue(), CHARS);
    }
}