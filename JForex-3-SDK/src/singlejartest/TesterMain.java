/*
 * Copyright (c) 2017 Dukascopy (Suisse) SA. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * -Redistribution of source code must retain the above copyright notice, this
 *  list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduce the above copyright notice,
 *  this list of conditions and the following disclaimer in the documentation
 *  and/or other materials provided with the distribution.
 * 
 * Neither the name of Dukascopy (Suisse) SA or the names of contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. DUKASCOPY (SUISSE) SA ("DUKASCOPY")
 * AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE
 * AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL DUKASCOPY OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE THIS SOFTWARE,
 * EVEN IF DUKASCOPY HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 */
package singlejartest;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dukascopy.api.Instrument;
import com.dukascopy.api.LoadingProgressListener;
import com.dukascopy.api.system.ISystemListener;
import com.dukascopy.api.system.ITesterClient;
import com.dukascopy.api.system.TesterFactory;

import forex.strategy.next.MacdSarStrategy;

/**
 * This small program demonstrates how to initialize Dukascopy tester and start
 * a strategy
 */
public class TesterMain {
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

	private static String jnlpUrl = "http://platform.dukascopy.com/demo/jforex.jnlp";
	private static String userName = "smasyEU";
	private static String password = "Deksi555";
	private static ITesterClient client;
	private static String reportsFileLocation = "report." + System.currentTimeMillis() + ".html";

	public static void main(String[] args) throws Exception {
		client = TesterFactory.getDefaultInstance();

		setSystemListener();
		tryToConnect();
		subscribeToInstruments();
		client.setInitialDeposit(Instrument.EURUSD.getPrimaryJFCurrency(), 10000);
		loadData();
		setDataInterval("2017/11/01 00:00:00", "2017/11/20 23:00:00");
		LOGGER.info("Starting strategy");

		// -- GOOD:
		// client.startStrategy(new SMASimpleStrategy(),getLoadingProgressListener());

		// -- GOOD: Profit:30
		client.startStrategy(new MacdSarStrategy(), getLoadingProgressListener());

		// ---------------------------

		// client.startStrategy(new SmaFibStrategyAIO(), getLoadingProgressListener());

		// client.startStrategy(new MaSarStrategy(), getLoadingProgressListener());

		// NO Chuta -- client.startStrategy(new MacdSmaSarStrategy(),
		// getLoadingProgressListener());

		// client.startStrategy(new PyramidStrategy(), getLoadingProgressListener());
		// client.startStrategy(new GoldenRainOct17(), getLoadingProgressListener());

		// client.startStrategy(new RsiCciStrategy(), getLoadingProgressListener());
		// client.startStrategy(new GridsMartingaleAndHedging(),
		// getLoadingProgressListener());
		// client.startStrategy(new sto_rc2(), getLoadingProgressListener());
		// client.startStrategy(new FibonacciTesting(), getLoadingProgressListener());

		// client.startStrategy(new GridsMartingaleAndHedging(),
		// getLoadingProgressListener());
	}

	private static void setDataInterval(String dateFrom, String dateTo) throws ParseException {
		final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

		Date dateFromObject = dateFormat.parse(dateFrom);
		Date dateToObject = dateFormat.parse(dateTo);

		client.setDataInterval(ITesterClient.DataLoadingMethod.ALL_TICKS, dateFromObject.getTime(),
				dateToObject.getTime());
		LOGGER.info("from: " + dateFrom.toString() + " to: " + dateTo.toString());
	}

	private static void setSystemListener() {
		client.setSystemListener(new ISystemListener() {
			@Override
			public void onStart(long processId) {
				LOGGER.info("Strategy started: " + processId);
			}

			@Override
			public void onStop(long processId) {
				LOGGER.info("Strategy stopped: " + processId);
				File reportFile = new File(reportsFileLocation);
				try {
					client.createReport(processId, reportFile);
				} catch (Exception e) {
					LOGGER.error(e.getMessage(), e);
				}
				if (client.getStartedStrategies().size() == 0) {
					System.exit(0);
				}
			}

			@Override
			public void onConnect() {
				LOGGER.info("Connected");
			}

			@Override
			public void onDisconnect() {
				// tester doesn't disconnect
			}
		});
	}

	private static void tryToConnect() throws Exception {
		LOGGER.info("Connecting...");
		// connect to the server using jnlp, user name and password
		// connection is needed for data downloading
		client.connect(jnlpUrl, userName, password);

		// wait for it to connect
		int i = 10; // wait max ten seconds
		while (i > 0 && !client.isConnected()) {
			Thread.sleep(1000);
			i--;
		}
		if (!client.isConnected()) {
			LOGGER.error("Failed to connect Dukascopy servers");
			System.exit(1);
		}
	}

	private static void subscribeToInstruments() {
		// set instruments that will be used in testing
		Set<Instrument> instruments = new HashSet<>();
		instruments.add(Instrument.EURUSD);
		instruments.add(Instrument.GBPJPY);
		// instruments.add(Instrument.AUDNZD);
		LOGGER.info("Subscribing instruments...");
		client.setSubscribedInstruments(instruments);
	}

	private static void loadData() throws InterruptedException, java.util.concurrent.ExecutionException {
		// load data
		LOGGER.info("Downloading data");
		Future<?> future = client.downloadData(null);
		// wait for downloading to complete
		future.get();
	}

	private static LoadingProgressListener getLoadingProgressListener() {
		return new LoadingProgressListener() {
			@Override
			public void dataLoaded(long startTime, long endTime, long currentTime, String information) {
				LOGGER.info(information);
			}

			@Override
			public void loadingFinished(boolean allDataLoaded, long startTime, long endTime, long currentTime) {
			}

			@Override
			public boolean stopJob() {
				return false;
			}
		};
	}
}
