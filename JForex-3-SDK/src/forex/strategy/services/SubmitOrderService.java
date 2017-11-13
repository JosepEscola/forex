package forex.strategy.services;

import com.dukascopy.api.Instrument;

public class SubmitOrderService {

	public static String getLabel(final Instrument instrument, final String id, final int counter) {
		return id + instrument.name().substring(0, 2) + instrument.name().substring(3, 5)
				+ String.format("%8d", counter).replace(" ", "0");
		// String label = instrument.name();
		// label = label + (counter++);
		// label = label.toUpperCase();
		// return label;
	}

}
