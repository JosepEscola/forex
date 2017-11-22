package forex.strategy.services;

import com.dukascopy.api.IConsole;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.JFException;

public class OnMessageService {

	private static final int MAX_ID_LENGTH = 5;

	public static String generateStrategyId(final String className) {
		return className.substring(0, MAX_ID_LENGTH);
	}

	public static void printOnMessage(final IConsole console, final IMessage message, final String id)
			throws JFException {
		// Print messages, but related to own orders
		if (message.getOrder() != null && message.getOrder().getLabel().substring(0, MAX_ID_LENGTH).equals(id)) {
			String orderLabel = message.getOrder().getLabel();
			IMessage.Type messageType = message.getType();
			switch (messageType) {
			// Ignore the following
			case ORDER_FILL_OK:
			case ORDER_CHANGED_OK:
				break;
			case ORDER_SUBMIT_OK:
				break;
			case ORDER_CLOSE_OK:
				console.getErr()
						.println(orderLabel + " : " + message.getOrder().getOrderCommand().toString() + " : "
								+ messageType + ": " + "Pips: " + message.getOrder().getProfitLossInPips() + " - $$$: "
								+ message.getOrder().getProfitLossInAccountCurrency() + "$");
				break;
			case ORDERS_MERGE_OK:
				console.getInfo().println(orderLabel + " " + messageType);
				break;
			case NOTIFICATION:
				console.getNotif().println(orderLabel + " " + message.getContent().replaceAll(".*-Order", "Order"));
				break;
			case ORDER_CHANGED_REJECTED:
			case ORDER_CLOSE_REJECTED:
			case ORDER_FILL_REJECTED:
			case ORDER_SUBMIT_REJECTED:
			case ORDERS_MERGE_REJECTED: {
				console.getWarn().println(orderLabel + " " + messageType + " - size:" + message.getReasons().size()
						+ " - cont:" + message.getContent());

				message.getReasons().forEach((reason) -> {
					console.getWarn().println("Reason:" + reason.toString());
				});
			}
				break;
			default:
				console.getErr().println(orderLabel + " *" + messageType + "* " + message.getContent());
				break;
			}
		}
	}
}
