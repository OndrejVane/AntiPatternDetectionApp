package cz.zcu.fav.kiv.antipatterndetectionapp.detecting.detectors;

import cz.zcu.fav.kiv.antipatterndetectionapp.detecting.DatabaseConnection;
import cz.zcu.fav.kiv.antipatterndetectionapp.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class VaryingSprintLengthDetectorImpl implements AntiPatternDetector {

    private final Logger LOGGER = LoggerFactory.getLogger(VaryingSprintLengthDetectorImpl.class);

    private final AntiPattern antiPattern = new AntiPattern(2L,
            "Varying Sprint Length",
            "VaryingSprintLength",
            "The length of the sprint changes very often. " +
                    "It is clear that iterations will be different " +
                    "lengths at the beginning and end of the project, " +
                    "but the length of the sprint should not change " +
                    "during the project.",
            new HashMap<>() {{
                put("maxDaysDifference", new Configuration<Integer>("maxDaysDifference",
                        "Max days difference",
                        "Maximum distance of two consecutive iterations in days", 7));
                put("maxIterationChanged", new Configuration<Integer>("maxIterationChanged",
                        "Max number of iteration changed",
                        "Maximum allowed number of significant changes in iteration lengths", 1));
            }});

    private final String sqlFileName = "varying_sprint_length.sql";
    // sql queries loaded from sql file
    private List<String> sqlQueries;

    private Integer getMaxDaysDifference(){
        return (Integer) this.antiPattern.getConfigurations().get("maxDaysDifference").getValue();
    }

    private Integer getMaxIterationChanged(){
        return (Integer) this.antiPattern.getConfigurations().get("maxIterationChanged").getValue();
    }

    @Override
    public AntiPattern getAntiPatternModel() {
        return this.antiPattern;
    }

    @Override
    public String getAntiPatternSqlFileName() {
        return this.sqlFileName;
    }

    @Override
    public void setSqlQueries(List<String> queries) {
        this.sqlQueries = queries;
    }

    /**
     * Postup detekce:
     *      1) naj??t v??echny iterace pro dann?? projekt se??azen?? dle name (start date m?? divn?? hodnoty a nejdou iterace po sob??)
     *      2) odebr??n?? prvn?? a posledn?? iterace z d??vodu mo??n??ch v??kyv??
     *      3) zjistit jejich d??lku (rozd??l mezi start date a end date)
     *      4) v??dy porovnat dv?? po sob?? jdouc?? iterace
     *      5) pokud se d??lka porovn??van??ch iterac?? li???? o v??ce ne?? 7 dn??, tak je zvednut counter
     *      6) pokud counter p??ekro???? hodnotu 2, tak je anti patern detekov??n
     * <p>
     * Alternativa (sledovat rozptyl d??lek jednotliv??ch iterac?? a pokud p??ekro???? n??jakou hodnotu, tak detevat)
     *
     * @param project            analyzovan?? project
     * @param databaseConnection datab??zov?? p??ipojen??
     * @return v??sledek detekce
     */
    @Override
    public QueryResultItem analyze(Project project, DatabaseConnection databaseConnection) {

        // init values
        List<ResultDetail> resultDetails = new ArrayList<>();
        int iterationLengthChanged = 0;
        int numberOfIterations = 0;

        try {
            ResultSet rs = databaseConnection.executeQueries(project, this.sqlQueries);
            if (rs != null) {
                int firstIterationLength = Integer.MIN_VALUE;
                int secondIterationLength;
                while (rs.next()) {
                    int iterationLength = rs.getInt("iterationLength");
                    numberOfIterations++;
                    if (firstIterationLength == Integer.MIN_VALUE) {
                        firstIterationLength = iterationLength;
                        continue;
                    } else {
                        secondIterationLength = iterationLength;
                    }

                    if (Math.abs(firstIterationLength - secondIterationLength) >= getMaxDaysDifference()) {
                        iterationLengthChanged = iterationLengthChanged + 1;
                    }
                    firstIterationLength = secondIterationLength;
                }
            }

        } catch (SQLException e) {
            LOGGER.error("Cannot read results from db");
            resultDetails.add(new ResultDetail("Problem in reading database", e.toString()));
            return new QueryResultItem(this.antiPattern, true, resultDetails);
        }

        resultDetails.add(new ResultDetail("Maximum iteration length change", String.valueOf(getMaxIterationChanged())));
        resultDetails.add(new ResultDetail("Count of iterations", String.valueOf(numberOfIterations)));
        resultDetails.add(new ResultDetail("Iteration length changed", String.valueOf(iterationLengthChanged)));


        if (iterationLengthChanged > getMaxIterationChanged()) {
            resultDetails.add(new ResultDetail("Conclusion", "Iteration length changed significantly too often"));
        } else {
            resultDetails.add(new ResultDetail("Conclusion", "Varying iteration length is all right"));
        }

        LOGGER.info(this.antiPattern.getPrintName());
        LOGGER.info(resultDetails.toString());

        return new QueryResultItem(this.antiPattern, (iterationLengthChanged > getMaxIterationChanged()), resultDetails);
    }
}
