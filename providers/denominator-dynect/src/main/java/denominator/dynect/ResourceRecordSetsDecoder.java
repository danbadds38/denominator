package denominator.dynect;

import static denominator.common.Util.peekingIterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import denominator.common.PeekingIterator;
import denominator.dynect.DynECT.Record;
import denominator.model.ResourceRecordSet;
import denominator.model.ResourceRecordSet.Builder;

class ResourceRecordSetsDecoder implements DynECTDecoder.Parser<Iterator<ResourceRecordSet<?>>> {
    @Override
    public Iterator<ResourceRecordSet<?>> apply(JsonReader reader) {
        JsonParser parser = new JsonParser();
        JsonElement data = parser.parse(reader);

        // there are 2 forms for record responses: an array of same type, or a
        // map per type.
        if (data.isJsonArray()) {
            return new GroupByRecordNameAndTypeIterator(data.getAsJsonArray().iterator());
        } else if (data.isJsonObject()) {
            List<JsonElement> elements = new ArrayList<JsonElement>();
            for (Entry<String, JsonElement> entry : data.getAsJsonObject().entrySet()) {
                if (entry.getValue() instanceof JsonArray) {
                    for (JsonElement element : entry.getValue().getAsJsonArray())
                        elements.add(element);
                }
            }
            return new GroupByRecordNameAndTypeIterator(elements.iterator());
        } else {
            throw new IllegalStateException("unknown format: " + data);
        }
    }

    static class GroupByRecordNameAndTypeIterator extends PeekingIterator<ResourceRecordSet<?>> {

        private final PeekingIterator<JsonElement> peekingIterator;

        public GroupByRecordNameAndTypeIterator(Iterator<JsonElement> sortedIterator) {
            this.peekingIterator = peekingIterator(sortedIterator);
        }

        @Override
        protected ResourceRecordSet<?> computeNext() {
            if (!peekingIterator.hasNext())
                return endOfData();
            JsonElement current = peekingIterator.next();
            Record record = ToRecord.INSTANCE.apply(current);
            Builder<Map<String, Object>> builder = ResourceRecordSet.builder()
                                                                    .name(record.name)
                                                                    .type(record.type)
                                                                    .ttl(record.ttl)
                                                                    .add(record.rdata);
            while (peekingIterator.hasNext()) {
                JsonElement next = peekingIterator.peek();
                if (next == null || next.isJsonNull())
                    continue;
                if (fqdnAndTypeEquals(current.getAsJsonObject(), next.getAsJsonObject())) {
                    peekingIterator.next();
                    builder.add(ToRecord.INSTANCE.apply(next).rdata);
                } else {
                    break;
                }
            }
            return builder.build();
        }
    }

    private static boolean fqdnAndTypeEquals(JsonObject current, JsonObject next) {
        return current.get("fqdn").equals(next.get("fqdn"))
                && current.get("record_type").equals(next.get("record_type"));
    }
}
