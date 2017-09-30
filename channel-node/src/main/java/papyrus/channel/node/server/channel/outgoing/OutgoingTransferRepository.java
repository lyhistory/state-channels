package papyrus.channel.node.server.channel.outgoing;

import java.math.BigInteger;

import org.springframework.stereotype.Repository;

import papyrus.channel.node.server.persistence.CassandraRepository;

@Repository
public class OutgoingTransferRepository extends CassandraRepository<BigInteger, OutgoingTransferBean> {
    public OutgoingTransferRepository() {
        super(OutgoingTransferBean.class);
    }
}
