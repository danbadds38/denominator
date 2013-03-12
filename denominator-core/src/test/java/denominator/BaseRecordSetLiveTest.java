package denominator;

import static com.google.common.base.Predicates.in;
import static com.google.common.collect.Iterators.any;
import static com.google.common.collect.Maps.filterKeys;
import static com.google.common.primitives.UnsignedInteger.fromIntBits;
import static denominator.model.ResourceRecordSets.nameAndType;
import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.util.Map;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.UnsignedInteger;

import denominator.model.ResourceRecordSet;

/**
 * extend this and initialize manager {@link BeforeClass}
 */
public abstract class BaseRecordSetLiveTest extends BaseProviderLiveTest {
    protected ImmutableList<String> supportedRecordTypes = ImmutableList.of("AAAA", "A", "CNAME", "MX", "NS", "PTR",
            "SPF", "SRV", "SSHFP", "TXT");

    protected Map<String, ResourceRecordSet<?>> stockRRSets() {
        return filterKeys(super.stockRRSets(), in(supportedRecordTypes));
    }

    /**
     * value at index 1 in the rrs is used for testing replace
     */
    @DataProvider(name = "simpleRecords")
    public Object[][] roundRobinRecords() {
        ImmutableList<ResourceRecordSet<?>> rrsets = ImmutableList.copyOf(stockRRSets().values());
        Object[][] data = new Object[rrsets.size()][1];
        for (int i = 0; i < rrsets.size(); i++)
            data[i][0] = rrsets.get(i);
        return data;
    }

    @Test(dataProvider = "simpleRecords")
    private void createNewRRS(ResourceRecordSet<?> recordSet) {
        skipIfNoCredentials();
        String zoneName = skipIfNoMutableZone();

        skipIfRRSetExists(zoneName, recordSet.getName(), recordSet.getType());

        UnsignedInteger ttl = fromIntBits(1800);

        rrsApi(zoneName).add(ResourceRecordSet.<Map<String, Object>> builder()
                                              .name(recordSet.getName())
                                              .type(recordSet.getType())
                                              .ttl(ttl)
                                              .add(recordSet.get(0)).build());

        Optional<ResourceRecordSet<?>> rrs = rrsApi(zoneName)
                .getByNameAndType(recordSet.getName(), recordSet.getType());

        assertPresent(rrs, zoneName, recordSet.getName(), recordSet.getType());

        checkRRS(rrs.get());
        assertEquals(rrs.get().getName(), recordSet.getName());
        assertEquals(rrs.get().getTTL().get(), ttl);
        assertEquals(rrs.get().getType(), recordSet.getType());
        assertEquals(rrs.get().size(), 1);
        assertEquals(rrs.get().get(0), recordSet.get(0));
    }

    @Test(dependsOnMethods = "createNewRRS", dataProvider = "simpleRecords")
    private void applyTTL(ResourceRecordSet<?> recordSet) {
        skipIfNoCredentials();
        String zoneName = skipIfNoMutableZone();

        rrsApi(zoneName).applyTTLToNameAndType(fromIntBits(200000), recordSet.getName(), recordSet.getType());

        Optional<ResourceRecordSet<?>> rrs = rrsApi(zoneName)
                .getByNameAndType(recordSet.getName(), recordSet.getType());

        assertPresent(rrs, zoneName, recordSet.getName(), recordSet.getType());

        checkRRS(rrs.get());
        assertEquals(rrs.get().getName(), recordSet.getName());
        assertEquals(rrs.get().getType(), recordSet.getType());
        assertEquals(rrs.get().getTTL().get().intValue(), 200000);
        assertEquals(rrs.get().size(), 1);
        assertEquals(rrs.get().get(0), recordSet.get(0));
    }

    @Test(dependsOnMethods = "applyTTL", dataProvider = "simpleRecords")
    private void replaceExistingRRSUpdatingTTL(ResourceRecordSet<?> recordSet) {
        skipIfNoCredentials();
        String zoneName = skipIfNoMutableZone();

        rrsApi(zoneName).replace(ResourceRecordSet.<Map<String, Object>> builder()
                                                  .name(recordSet.getName())
                                                  .type(recordSet.getType())
                                                  .ttl(10000)
                                                  .add(recordSet.get(1)).build());

        Optional<ResourceRecordSet<?>> rrs = rrsApi(zoneName)
                .getByNameAndType(recordSet.getName(), recordSet.getType());

        assertPresent(rrs, zoneName, recordSet.getName(), recordSet.getType());

        checkRRS(rrs.get());
        assertEquals(rrs.get().getName(), recordSet.getName());
        assertEquals(rrs.get().getType(), recordSet.getType());
        assertEquals(rrs.get().getTTL().get().intValue(), 10000);
        assertEquals(rrs.get().size(), 1);
        assertEquals(rrs.get().get(0), recordSet.get(1));
    }

    @Test(dependsOnMethods = "replaceExistingRRSUpdatingTTL", dataProvider = "simpleRecords")
    private void removeLastRecordFromExistingRRSRemovesRRS(ResourceRecordSet<?> recordSet) {
        skipIfNoCredentials();
        String zoneName = skipIfNoMutableZone();

        rrsApi(zoneName).remove(ResourceRecordSet.<Map<String, Object>> builder()
                                                 .name(recordSet.getName())
                                                 .type(recordSet.getType())
                                                 .add(recordSet.get(1)).build());

        Optional<ResourceRecordSet<?>> rrs = rrsApi(zoneName)
                .getByNameAndType(recordSet.getName(), recordSet.getType());

        assertFalse(
                rrs.isPresent(),
                format("recordset(%s, %s) still present in zone(%s)", recordSet.getName(), recordSet.getType(),
                        zoneName));
    }

    @Test(dataProvider = "simpleRecords")
    private void deleteRRS(ResourceRecordSet<?> recordSet) {
        skipIfNoCredentials();
        String zoneName = skipIfNoMutableZone();
        String recordName = recordSet.getName().replace("." + zoneName, "-delete." + zoneName);

        skipIfRRSetExists(zoneName, recordName, recordSet.getType());

        rrsApi(zoneName).add(ResourceRecordSet.<Map<String, Object>> builder()
                                              .name(recordName)
                                              .type(recordSet.getType())
                                              .add(recordSet.get(0)).build());

        Optional<ResourceRecordSet<?>> rrs = rrsApi(zoneName).getByNameAndType(recordName, recordSet.getType());

        assertPresent(rrs, zoneName, recordName, recordSet.getType());

        rrsApi(zoneName).deleteByNameAndType(recordName, recordSet.getType());

        String failureMessage = format("recordset(%s, %s) still exists in zone(%s)", recordName, recordSet.getType(),
                zoneName);
        assertFalse(rrsApi(zoneName).getByNameAndType(recordName, recordSet.getType()).isPresent(), failureMessage);
        assertFalse(any(rrsApi(zoneName).list(), nameAndType(recordName, recordSet.getType())), failureMessage);

        // test no exception if re-applied
        rrsApi(zoneName).deleteByNameAndType(recordName, recordSet.getType());
    }
}
