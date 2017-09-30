package papyrus.channel.node.server.channel.outgoing;

import org.springframework.stereotype.Repository;
import org.web3j.abi.datatypes.Address;

import papyrus.channel.node.server.persistence.CassandraRepository;

@Repository
public class OutgoingChannelRepository extends CassandraRepository<Address, OutgoingChannelBean> {
    public OutgoingChannelRepository() {
        super(OutgoingChannelBean.class);
    }
}
