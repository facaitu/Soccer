package predictions;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;

import com.gargoylesoftware.htmlunit.javascript.host.media.AnalyserNode;

import entries.FinalEntry;
import main.Analysis;
import main.Fixture;
import main.SQLiteJDBC;
import main.Test.DataType;
import runner.RunnerAsianPredictions;
import runner.RunnerPredictions;
import scraper.FullOddsCollector;
import scraper.Scraper;
import settings.Settings;
import utils.Stats;
import utils.Utils;
import xls.XlSUtils;

public class Predictions {

	public static ArrayList<String> CHECKLIST = new ArrayList<>();

	public static void main(String[] args) throws Exception {

		// CHECKLIST.add("ENG");
		 CHECKLIST.add("ENG2");
		// CHECKLIST.add("ENG3");
		// CHECKLIST.add("ENG4");
		// CHECKLIST.add("ENG5");
		// CHECKLIST.add("ENG");
//		CHECKLIST.add("IT");
		// CHECKLIST.add("IT2");
		// CHECKLIST.add("FR");
		// CHECKLIST.add("FR2");
		// CHECKLIST.add("SPA");
		// CHECKLIST.add("SPA2");
		// CHECKLIST.add("GER");
		// CHECKLIST.add("GER2");
		// CHECKLIST.add("SCO");
		// CHECKLIST.add("NED");
		// CHECKLIST.add("BEL");
		// CHECKLIST.add("SWI");
		// CHECKLIST.add("POR");
		// CHECKLIST.add("GRE");
		// CHECKLIST.add("TUR");
		// CHECKLIST.add("BUL");
		// CHECKLIST.add("RUS");
		// CHECKLIST.add("AUS");
		// CHECKLIST.add("DEN");
		// CHECKLIST.add("CZE");
		// CHECKLIST.add("ARG");
		// CHECKLIST.add("POL");
		// CHECKLIST.add("CRO");
		// CHECKLIST.add("SLO");
		// CHECKLIST.add("USA");
		// CHECKLIST.add("SWE");
		// CHECKLIST.add("NOR");
		// CHECKLIST.add("FIN");
		// CHECKLIST.add("BRA");
		// CHECKLIST.add("BRB");

		// Scraper.updateInParallel(CHECKLIST, 2, OnlyTodayMatches.FALSE,
		// UpdateType.AUTOMATIC, 19, 1);
		// predictions(2017, DataType.ODDSPORTAL, UpdateType.AUTOMATIC,
		// OnlyTodayMatches.TRUE, 20, 1);
		// predictions(2017, DataType.ODDSPORTAL, UpdateType.MANUAL,
		// OnlyTodayMatches.TRUE, 21, 01);

		 Scraper.updateDB(CHECKLIST, 2, OnlyTodayMatches.TRUE, UpdateType.AUTOMATIC,
		 26, 1);

//		predictionsFromDB(2017, UpdateType.AUTOMATIC, OnlyTodayMatches.TRUE, 26, 1);

		// ArrayList<Fixture> nexts = FullOddsCollector.of("IT",
		// 2017).nextMatches(OnlyTodayMatches.TRUE);
		// SQLiteJDBC.storeFixtures(nexts,2017);

		// Scraper.checkAndUpdate("ENG", OnlyTodayMatches.FALSE);
		//
		// // Runtime.getRuntime().exec("taskkill /F /IM chromedriver.exe /T");
		//
	}

	public static ArrayList<FinalEntry> predictionsFromDB(int year, UpdateType automatic, OnlyTodayMatches onlyToday,
			int day, int month) throws InterruptedException, ExecutionException, IOException {

		ArrayList<FinalEntry> all = new ArrayList<>();

		ArrayList<String> leagues = automatic.equals(UpdateType.AUTOMATIC) ? Scraper.getTodaysLeagueList(day, month)
				: CHECKLIST;
		System.out.println(leagues);

		for (String league : leagues) {
			long start = System.currentTimeMillis();
			ArrayList<Fixture> fixtures = SQLiteJDBC.selectFixtures(league, year);
			System.out.println((System.currentTimeMillis() - start) / 1000d + "sec");
			all.addAll(Analysis.predict(fixtures, league, year));
		}

		all.sort(Comparator.comparing(FinalEntry::getPrediction));

		ArrayList<FinalEntry> result = new ArrayList<>();
		HashMap<String, ArrayList<FinalEntry>> byLeague = Utils.byLeague(all);

		for (Entry<String, ArrayList<FinalEntry>> i : byLeague.entrySet()) {

			ArrayList<FinalEntry> data = Utils.notPendingFinals(i.getValue());
			ArrayList<FinalEntry> equilibriumsData = Utils.equilibriums(data);
			ArrayList<FinalEntry> dataProper = Utils.noequilibriums(data);

			ArrayList<FinalEntry> pending = Utils.pendingFinals(i.getValue());
			if (onlyToday.equals(OnlyTodayMatches.TRUE)) {
				pending = Utils.gamesForDay(pending, LocalDate.of(2018, month, day));
			}

			if (pending.isEmpty())
				continue;

			System.out.println(i.getKey());
			ArrayList<FinalEntry> equilibriumsPending = Utils.equilibriums(pending);
			ArrayList<FinalEntry> pendingProper = Utils.noequilibriums(pending);

			boolean allUnders = false;
			boolean allOvers = false;
			if (Utils.getProfit(Utils.allUnders(Utils.onlyFixtures(equilibriumsData))) > 0f) {
				allUnders = true;
				Utils.printStats(Utils.allUnders(Utils.onlyFixtures(equilibriumsData)), "Equilibriums as unders");
			} else if (Utils.getProfit(Utils.allOvers(Utils.onlyFixtures(equilibriumsData))) > 0f) {
				allOvers = true;
				Utils.printStats(Utils.allOvers(Utils.onlyFixtures(equilibriumsData)), "Equilibriums as overs");
			} else {
				System.out.println("No value in equilibriums");
			}

			if (allUnders) {
				System.out.println(equilibriumsPending);
				result.addAll(equilibriumsPending);
			} else if (allOvers) {
				equilibriumsPending = XlSUtils.restrict(equilibriumsPending,
						Settings.shots(i.getKey()).withTHandBounds(0.45f));
				System.out.println(equilibriumsPending);
				result.addAll(equilibriumsPending);
			}

			Utils.printStats(dataProper, "all");
			Utils.printStats(Utils.onlyUnders(dataProper), "unders");
			result.addAll(pendingProper);
			ArrayList<FinalEntry> pendingUnders = Utils.onlyUnders(pendingProper);
			if(!pendingUnders.isEmpty())
				System.out.println(pendingUnders);
			Utils.printStats(Utils.onlyOvers(dataProper), "overs");
			ArrayList<FinalEntry> pendingOvers = Utils.onlyOvers(pendingProper);
			if(!pendingOvers.isEmpty())
				System.out.println(pendingOvers);
			
			ArrayList<FinalEntry> vop = Analysis.valueOverPinnacle(dataProper, true, 1.0f).all;
			System.out.println(new Stats(vop, "value over pinn with predict > 1.0"));
			Utils.printStats(Utils.onlyUnders(vop), "unders");
			Utils.printStats(Utils.onlyOvers(vop), "overs");

			System.out
					.println("---------------------------------------------------------------------------------------");
		}

		result.sort(Comparator.comparing(FinalEntry::getPrediction));

		System.out.println(result);

		System.out.println(Analysis.valueOverPinnacle(result, true, 1.0f).all);
		return all;
	}

	public static ArrayList<FinalEntry> predictions(int year, DataType type, UpdateType automatic,
			OnlyTodayMatches onlyToday, int day, int month)
			throws InterruptedException, ExecutionException, IOException {
		String base = new File("").getAbsolutePath();

		FileInputStream file = null;
		if (type.equals(DataType.ALLEURODATA))
			file = new FileInputStream(new File(base + "/data/all-euro-data-" + year + "-" + (year + 1) + ".xls"));
		else if (type.equals(DataType.ODDSPORTAL))
			file = new FileInputStream(new File(base + "/data/odds" + year + ".xls"));

		HSSFWorkbook workbook = new HSSFWorkbook(file);
		Iterator<Sheet> sheet = workbook.sheetIterator();
		ArrayList<FinalEntry> all = new ArrayList<>();

		ExecutorService pool = Executors.newFixedThreadPool(1);
		ArrayList<Future<ArrayList<FinalEntry>>> threadArray = new ArrayList<Future<ArrayList<FinalEntry>>>();
		ArrayList<String> leagues = automatic.equals(UpdateType.AUTOMATIC) ? Scraper.getTodaysLeagueList(day, month)
				: CHECKLIST;
		System.out.println(leagues);
		while (sheet.hasNext()) {
			HSSFSheet sh = (HSSFSheet) sheet.next();
			// if (!sh.getSheetName().equals("ENG2"))
			// continue;
			if (!leagues.contains(sh.getSheetName()))
				continue;

			threadArray.add(pool.submit(new RunnerPredictions(sh, year)));
		}

		for (Future<ArrayList<FinalEntry>> fd : threadArray) {
			all.addAll(fd.get());
			// System.out.println("Total profit: " + String.format("%.2f",
			// totalProfit));
		}
		// System.out.println("Total profit for season " + year + " is " +
		// String.format("%.2f", totalProfit));
		workbook.close();
		file.close();
		pool.shutdown();

		all.sort(Comparator.comparing(FinalEntry::getPrediction));

		ArrayList<FinalEntry> result = new ArrayList<>();
		HashMap<String, ArrayList<FinalEntry>> byLeague = Utils.byLeague(all);

		for (Entry<String, ArrayList<FinalEntry>> i : byLeague.entrySet()) {

			ArrayList<FinalEntry> data = Utils.notPendingFinals(i.getValue());
			ArrayList<FinalEntry> equilibriumsData = Utils.equilibriums(data);
			ArrayList<FinalEntry> dataProper = Utils.noequilibriums(data);

			ArrayList<FinalEntry> pending = Utils.pendingFinals(i.getValue());
			if (onlyToday.equals(OnlyTodayMatches.TRUE)) {
				pending = Utils.gamesForDay(pending, LocalDate.of(2018, month, day));
			}

			if (pending.isEmpty())
				continue;

			System.out.println(i.getKey());
			ArrayList<FinalEntry> equilibriumsPending = Utils.equilibriums(pending);
			ArrayList<FinalEntry> pendingProper = Utils.noequilibriums(pending);

			boolean allUnders = false;
			boolean allOvers = false;
			if (Utils.getProfit(Utils.allUnders(Utils.onlyFixtures(equilibriumsData))) > 0f) {
				allUnders = true;
				Utils.printStats(Utils.allUnders(Utils.onlyFixtures(equilibriumsData)), "Equilibriums as unders");
			} else if (Utils.getProfit(Utils.allOvers(Utils.onlyFixtures(equilibriumsData))) > 0f) {
				allOvers = true;
				Utils.printStats(Utils.allOvers(Utils.onlyFixtures(equilibriumsData)), "Equilibriums as overs");
			} else {
				System.out.println("No value in equilibriums");
			}

			if (allUnders) {
				System.out.println(equilibriumsPending);
				result.addAll(equilibriumsPending);
			} else if (allOvers) {
				equilibriumsPending = XlSUtils.restrict(equilibriumsPending,
						Settings.shots(i.getKey()).withTHandBounds(0.45f));
				System.out.println(equilibriumsPending);
				result.addAll(equilibriumsPending);
			}

			Utils.printStats(dataProper, "all");
			Utils.printStats(Utils.onlyUnders(dataProper), "unders");
			System.out.println(Utils.onlyUnders(pendingProper));
			result.addAll(pendingProper);
			Utils.printStats(Utils.onlyOvers(dataProper), "overs");
			System.out.println(Utils.onlyOvers(pendingProper));
			System.out
					.println("---------------------------------------------------------------------------------------");
		}

		result.sort(new Comparator<FinalEntry>() {

			@Override
			public int compare(FinalEntry o1, FinalEntry o2) {

				return ((Float) o2.prediction).compareTo((Float) o1.prediction);
			}

		});
		System.out.println(result);

		return all;
	}

	public static float asianPredictions(int year, boolean parsedLeagues)
			throws InterruptedException, ExecutionException, IOException {
		String base = new File("").getAbsolutePath();

		FileInputStream file;
		if (!parsedLeagues)
			file = new FileInputStream(new File(base + "\\data\\all-euro-data-" + year + "-" + (year + 1) + ".xls"));
		else
			file = new FileInputStream(new File(base + "\\data\\odds" + year + ".xls"));

		HSSFWorkbook workbook = new HSSFWorkbook(file);
		Iterator<Sheet> sheet = workbook.sheetIterator();
		float totalProfit = 0.0f;

		ExecutorService pool = Executors.newFixedThreadPool(3);
		ArrayList<Future<Float>> threadArray = new ArrayList<Future<Float>>();
		while (sheet.hasNext()) {
			HSSFSheet sh = (HSSFSheet) sheet.next();
			// if (!sh.getSheetName().equals("ENG2"))
			// continue;
			if (!Predictions.CHECKLIST.contains(sh.getSheetName()))
				continue;

			threadArray.add(pool.submit(new RunnerAsianPredictions(sh, year)));
		}

		for (Future<Float> fd : threadArray) {
			totalProfit += fd.get();
			// System.out.println("Total profit: " + String.format("%.2f",
			// totalProfit));
		}
		// System.out.println("Total profit for season " + year + " is " +
		// String.format("%.2f", totalProfit));
		workbook.close();
		file.close();
		pool.shutdown();
		return totalProfit;
	}

	public enum OnlyTodayMatches {
		TRUE, FALSE
	}

}
