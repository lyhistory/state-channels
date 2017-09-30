package papyrus.channel.node.server.persistence;

import java.nio.ByteBuffer;

import org.web3j.abi.datatypes.Address;
import org.web3j.utils.Numeric;

import com.datastax.driver.core.DataType;
import com.datastax.driver.core.ProtocolVersion;
import com.datastax.driver.core.TypeCodec;
import com.datastax.driver.core.exceptions.InvalidTypeException;

public class AddressCodec extends TypeCodec<Address> {

    private static final int BYTES = 20;
    private static final int CHARS = BYTES * 2;

    public AddressCodec() {
        super(DataType.blob(), Address.class);
    }

    @Override
    public ByteBuffer serialize(Address value, ProtocolVersion protocolVersion) throws InvalidTypeException {
        if (value == null) return null;
        byte[] bytes = Numeric.toBytesPadded(value.getValue(), BYTES);
        return ByteBuffer.wrap(bytes);
    }

    @Override
    public Address deserialize(ByteBuffer bytes, ProtocolVersion protocolVersion) throws InvalidTypeException {
        if (bytes == null) return null;
        return new Address(Numeric.toBigInt(bytes.array(), 0, BYTES));
    }

    @Override
    public Address parse(String value) throws InvalidTypeException {
        return new Address(value);
    }

    @Override
    public String format(Address value) throws InvalidTypeException {
        return Numeric.toHexStringNoPrefixZeroPadded(value.getValue(), CHARS);
    }
}