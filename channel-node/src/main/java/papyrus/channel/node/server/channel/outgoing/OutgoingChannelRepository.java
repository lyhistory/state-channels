package papyrus.channel.node.server.channel.outgoing;

import org.springframework.stereotype.Repository;
import org.web3j.abi.datatypes.Address;

import com.datastax.driver.mapping.Result;
import com.datastax.driver.mapping.annotations.Accessor;
import com.datastax.driver.mapping.annotations.Query;

import papyrus.channel.node.server.persistence.CassandraRepository;

@Repository
public class OutgoingChannelRepository extends CassandraRepository<Address, OutgoingChannelBean> {

    private Queries queries;
    
    public OutgoingChannelRepository() {
        super(OutgoingChannelBean.class);
    }

    @Override
    protected void init() {
        super.init();
        queries = mappingManager.createAccessor(Queries.class);
    }

    public Iterable<OutgoingChannelBean> all() {
        return queries.getAll();
    }

    @Accessor
    interface Queries {
        @Query("select * from outgoing.channel")
        Result<OutgoingChannelBean> getAll();
    }
}
