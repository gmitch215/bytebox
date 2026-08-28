package dev.gmitch215.bytebox.binding;

import dev.gmitch215.bytebox.js.TSObject;
import java.util.List;
import org.teavm.jso.JSObject;

/**
 * An Analytics Engine dataset. Declared with {@code analytics()}, named {@code ANALYTICS} by
 * default.
 *
 * <p>The one binding here that does not block. Writes are fire and forget: nothing is returned, no
 * promise settles, and a failure is not reported. Reading the data back is a SQL API call from
 * outside the Worker rather than anything on this binding.
 *
 * <p>A data point carries at most 20 doubles, 20 blobs and one index, where the index is what
 * queries group by.
 *
 * {@snippet lang = "java":
 * env.analytics().write(DataPoint.of()
 * 	.index(request.getHeaders().get("cf-ipcountry"))
 * 	.blob(request.getUrl())
 * 	.number(response.getStatus()));
 *}
 *
 * @since 1.0.0
 */
public interface AnalyticsEngine extends JSObject {
	/**
	 * Records one data point.
	 *
	 * @param point the point
	 */
	void writeDataPoint(TSObject point);

	/**
	 * Records one data point.
	 *
	 * @param point the point
	 */
	default void write(DataPoint point) {
		writeDataPoint(point.build());
	}

	/**
	 * A data point under construction.
	 *
	 * @since 1.0.0
	 */
	final class DataPoint {

		private final TSObject blobs = TSObject.array();
		private final TSObject doubles = TSObject.array();
		private String index;
		private int blobCount;
		private int doubleCount;

		private DataPoint() {}

		/** {@return an empty data point} */
		public static DataPoint of() {
			return new DataPoint();
		}

		/**
		 * Sets the index queries group by. At most one, and at most 96 bytes.
		 *
		 * @param value the index
		 * @return this point
		 */
		public DataPoint index(String value) {
			index = value;
			return this;
		}

		/**
		 * Adds a string field. At most 20, and 5120 bytes across all of them.
		 *
		 * @param value the value
		 * @return this point
		 */
		public DataPoint blob(String value) {
			if (blobCount == 20) {
				throw new IllegalStateException("a data point carries at most 20 blobs");
			}
			blobs.push(TSObject.of(value));
			blobCount++;
			return this;
		}

		/**
		 * Adds a numeric field. At most 20.
		 *
		 * @param value the value
		 * @return this point
		 */
		public DataPoint number(double value) {
			if (doubleCount == 20) {
				throw new IllegalStateException("a data point carries at most 20 doubles");
			}
			doubles.push(TSObject.of(value));
			doubleCount++;
			return this;
		}

		TSObject build() {
			TSObject point = TSObject.object();
			if (blobCount > 0) point.set("blobs", blobs);
			if (doubleCount > 0) point.set("doubles", doubles);
			if (index != null) point.set("indexes", TSObject.array(List.of(index)));
			return point;
		}
	}
}
