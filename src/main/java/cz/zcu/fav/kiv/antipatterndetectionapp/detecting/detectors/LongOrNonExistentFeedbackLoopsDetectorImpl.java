package cz.zcu.fav.kiv.antipatterndetectionapp.detecting.detectors;

import cz.zcu.fav.kiv.antipatterndetectionapp.detecting.DatabaseConnection;
import cz.zcu.fav.kiv.antipatterndetectionapp.model.*;
import cz.zcu.fav.kiv.antipatterndetectionapp.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LongOrNonExistentFeedbackLoopsDetectorImpl implements AntiPatternDetector {

    private final Logger LOGGER = LoggerFactory.getLogger(LongOrNonExistentFeedbackLoopsDetectorImpl.class);

    private final AntiPattern antiPattern = new AntiPattern(6L,
            "Long Or Non Existent Feedback Loops",
            "LongOrNonExistentFeedbackLoops",
            "Long spacings between customer feedback or no feedback. The customer " +
                    "enters the project and sees the final result. In the end, the customer " +
                    "may not get what he really wanted. With long intervals of feedback, " +
                    "some misunderstood functionality can be created and we have to spend " +
                    "a lot of effort and time to redo it. ",
            new HashMap<>() {{
                put("divisionOfIterationsWithFeedbackLoop", new Configuration<Float>("divisionOfIterationsWithFeedbackLoop",
                        "Division of iterations with feedback loop",
                        "Minimum percentage of the total number of iterations with feedback loop (0,1)", 0.5f));
                put("maxGapBetweenFeedbackLoopRate", new Configuration<Float>("maxGapBetweenFeedbackLoopRate",
                        "Maximum gap between feedback loop rate",
                        "Value that multiplies average iteration length for given project. Result" +
                                " is maximum threshold value for gap between feedback loops in days.", 2f));
            }}
            );

    private final String SQL_FILE_NAME = "long_or_non_existent_feedback_loops.sql";
    // sql queries loaded from sql file
    private List<String> sqlQueries;

    private float getDivisionOfIterationsWithFeedbackLoop() {
        return (float) antiPattern.getConfigurations().get("divisionOfIterationsWithFeedbackLoop").getValue();
    }

    private float getMaxGapBetweenFeedbackLoopRate() {
        return (float) antiPattern.getConfigurations().get("maxGapBetweenFeedbackLoopRate").getValue();
    }

    @Override
    public AntiPattern getAntiPatternModel() {
        return this.antiPattern;
    }

    @Override
    public String getAntiPatternSqlFileName() {
        return this.SQL_FILE_NAME;
    }

    @Override
    public void setSqlQueries(List<String> queries) {
        this.sqlQueries = queries;
    }

    /**
     * Postup detekce:
     * 1) naj??t v??echny aktivity, kter?? by mohli p??edstavovat z??kaznick?? demo (n??zev bude obsahovat substring)
     * 2) zjistit pr??m??rnou d??lku iterac??
     * 3) zjistit po??et iterac??
     * 4) nejprve porovnat s po??tem iterac?? => iterace a nalezen?? aktivity by se m??ly ide??ln?? rovnat (mohou b??t men???? i v??t???? ale n?? o moc men????)
     * 5) u ka??d??ch dvou po sob?? jdouc??ch aktivit??ch ud??lat rozd??l datum?? a porovnat s pr??m??rnou d??lkou iterace => rozd??l by se nem??l moc li??it od pr????rn?? d??lky iterace
     * 6) pokud u bodu 4) dojde k detekci m??leho po??tu nalezen??ch aktivit (t??m ned??l?? aktivity na sch??zky a m????e zaznamen??vat pouze do wiki)
     * 7) naj??t v??echny wiki str??nky a ud??lat join kdy se m??nily (m????e b??t pou??ita jedn?? str??nka pro v??ce sch??zek) s p????slu??n??m n??zvem
     * 8) ud??lat group podle dne
     *
     * @param project            analyzovan?? project
     * @param databaseConnection datab??zov?? p??ipojen??
     * @return v??sledek detekce
     */
    @Override
    public QueryResultItem analyze(Project project, DatabaseConnection databaseConnection) {

        // init values
        long totalNumberIterations = 0;
        int averageIterationLength = 0;
        int numberOfIterationsWhichContainsAtLeastOneActivityForFeedback = 0;
        int numberOfIterationsWhichContainsAtLeastOneWikiPageForFeedback = 0;
        List<Date> feedbackActivityEndDates = new ArrayList<>();
        List<Date> feedbackWikiPagesEndDates = new ArrayList<>();
        Date projectStartDate = null;
        Date projectEndDate = null;

        List<List<Map<String, Object>>> resultSets = databaseConnection.executeQueriesWithMultipleResults(project, this.sqlQueries);
        for (int i = 0; i < resultSets.size(); i++) {
            List<Map<String, Object>> rs = resultSets.get(i);

            switch (i) {
                case 0:
                    totalNumberIterations = (long) rs.get(0).get("numberOfIterations");
                    break;
                case 1:
                    averageIterationLength = ((BigDecimal) rs.get(0).get("averageIterationLength")).intValue();
                    break;
                case 2:
                    if (rs.size() != 0) {
                        numberOfIterationsWhichContainsAtLeastOneActivityForFeedback = ((Long) rs.get(0).get("totalCountOfIterationsWithFeedbackActivity")).intValue();
                    }
                    break;
                case 3:
                    Date activityEndDate;
                    for (Map<String, Object> map : rs) {
                        activityEndDate = (Date) map.get("endDate");
                        feedbackActivityEndDates.add(activityEndDate);
                    }
                    break;
                case 4:
                    projectStartDate = (Date) rs.get(0).get("startDate");
                    break;
                case 5:
                    projectEndDate = (Date) rs.get(0).get("endDate");
                    break;
                case 6:
                    numberOfIterationsWhichContainsAtLeastOneWikiPageForFeedback = rs.size();
                    Date wikiPageEndDate;
                    for (Map<String, Object> map : rs) {
                        wikiPageEndDate = (Date) map.get("appointmentDate");
                        feedbackWikiPagesEndDates.add(wikiPageEndDate);
                    }
                    break;
                default:

            }
        }

        double halfNumberOfIterations = totalNumberIterations * getDivisionOfIterationsWithFeedbackLoop();

        // pokud je po??et iterac??, kter?? obsahuj?? alespo?? jednu aktivitu s feedbackem, tak je to ide??ln?? p????pad
        if (totalNumberIterations <= numberOfIterationsWhichContainsAtLeastOneActivityForFeedback) {
            List<ResultDetail> resultDetails = Utils.createResultDetailsList(
                    new ResultDetail("Number of iterations", Long.toString(totalNumberIterations)),
                    new ResultDetail("Number of iterations with feedback loops", Integer.toString(numberOfIterationsWhichContainsAtLeastOneActivityForFeedback)),
                    new ResultDetail("Conclusion", "In each iteration is at least one activity that represents feedback loop"));


            return new QueryResultItem(this.antiPattern, false, resultDetails);

            // pokud alespo?? v polovin?? iterac??ch do??lo ke kontaktu se z??kazn??kem => zkontrolovat rozestupy
        } else if (feedbackActivityEndDates.size() > halfNumberOfIterations) {

            Date firstDate = projectStartDate;
            Date secondDate = null;

            for (Date feedbackActivityDate : feedbackActivityEndDates) {
                secondDate = feedbackActivityDate;
                long daysBetween = Utils.daysBetween(firstDate, secondDate);
                firstDate = secondDate;

                if (daysBetween >= getMaxGapBetweenFeedbackLoopRate() * averageIterationLength) {
                    List<ResultDetail> resultDetails = Utils.createResultDetailsList(
                            new ResultDetail("Days between", Long.toString(daysBetween)),
                            new ResultDetail("Average iteration length", Integer.toString(averageIterationLength)),
                            new ResultDetail("Conclusion", "Customer feedback loop is too long"));


                    return new QueryResultItem(this.antiPattern, true, resultDetails);
                }
            }

            // rozestupy feedback?? jsou ok
            List<ResultDetail> resultDetails = Utils.createResultDetailsList(
                    new ResultDetail("Average iteration length", Integer.toString(averageIterationLength)),
                    new ResultDetail("Conclusion", "Customer feedback has been detected and there is not too much gap between them"));


            return new QueryResultItem(this.antiPattern, false, resultDetails);

            // bylo nalezeno p????li?? m??lo aktivit => zkusit se pod??vat ve wiki str??nk??ch
        } else {

            // pokud je v ka??d?? iteraci alespo?? jeda wiki str??nka nazna??uj??c?? sch??zku => ide??ln?? p????pad
            if (numberOfIterationsWhichContainsAtLeastOneWikiPageForFeedback >= totalNumberIterations) {
                List<ResultDetail> resultDetails = Utils.createResultDetailsList(
                        new ResultDetail("Number of iterations", Long.toString(totalNumberIterations)),
                        new ResultDetail("Number of iterations with feedback loops", Integer.toString(numberOfIterationsWhichContainsAtLeastOneWikiPageForFeedback)),
                        new ResultDetail("Conclusion", "In each iteration is created/edited at least one wiki page that represents feedback loop"));

                return new QueryResultItem(this.antiPattern, false, resultDetails);
            }

            // pokud alespo?? v polovin?? iterac??ch do??lo ke kontaktu se z??kazn??kem => zkontrolovat rozestupy
            Date firstDate = projectStartDate;
            Date secondDate = null;

            for (Date feedbackWikipagesDate : feedbackWikiPagesEndDates) {
                secondDate = feedbackWikipagesDate;
                long daysBetween = Utils.daysBetween(firstDate, secondDate);
                firstDate = secondDate;

                if (daysBetween >= getMaxGapBetweenFeedbackLoopRate() * averageIterationLength) {
                    List<ResultDetail> resultDetails = Utils.createResultDetailsList(
                            new ResultDetail("Days between", Long.toString(daysBetween)),
                            new ResultDetail("Average iteration length", Integer.toString(averageIterationLength)),
                            new ResultDetail("Conclusion", "Customer feedback loop is too long"));


                    return new QueryResultItem(this.antiPattern, true, resultDetails);
                }
            }
            // rozestupy feedback?? jsou ok
            List<ResultDetail> resultDetails = Utils.createResultDetailsList(
                    new ResultDetail("Average iteration length", Integer.toString(averageIterationLength)),
                    new ResultDetail("Conclusion", "Customer feedback has been detected and there is not too much gap between them"));


            return new QueryResultItem(this.antiPattern, false, resultDetails);
        }
    }
}
