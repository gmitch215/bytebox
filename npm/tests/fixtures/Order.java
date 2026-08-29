package fixture;

import dev.gmitch215.bytebox.js.TSObject;
import dev.gmitch215.bytebox.json.Codec;
import dev.gmitch215.bytebox.json.JSONType;
import java.util.List;

/**
 * A record with one field of every shape a codec has to carry.
 *
 * <p>The codec below is written by hand and deliberately identical in shape to what
 * {@code generateCodecs} emits, so this fixture can exercise the runtime half without running the
 * plugin. The generator's own output is checked where it is written, in the plugin's functional
 * tests.
 */
@JSONType
public record Order(
	String sku,
	int quantity,
	long total,
	double weight,
	boolean paid,
	Status status,
	List<String> tags
) {
	/** An enum field, which a codec carries by name. */
	public enum Status {
		/** Not yet paid for. */
		PENDING,
		/** Paid for and on its way. */
		SHIPPED
	}

	/** Shaped exactly as the generator writes one. */
	public static final class OrderCodec implements Codec<Order> {

		@Override
		public Order decode(TSObject value) {
			return new Order(
				value.get("sku").asString(),
				value.get("quantity").asInt(),
				value.get("total").asLong(),
				value.get("weight").asDouble(),
				value.get("paid").asBoolean(),
				Status.valueOf(value.get("status").asString()),
				value.get("tags").asStringList()
			);
		}

		@Override
		public TSObject encode(Order value) {
			TSObject encoded = TSObject.object();
			encoded.set("sku", TSObject.from(value.sku()));
			encoded.set("quantity", TSObject.from(value.quantity()));
			encoded.set("total", TSObject.from(value.total()));
			encoded.set("weight", TSObject.from(value.weight()));
			encoded.set("paid", TSObject.from(value.paid()));
			encoded.set("status", TSObject.of(value.status().name()));
			encoded.set("tags", TSObject.array(value.tags()));
			return encoded;
		}
	}
}
