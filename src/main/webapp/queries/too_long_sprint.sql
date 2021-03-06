/*
Anti-pattern name: Too Long Sprint

Description: Iterations too long. (ideal iteration length is about 1-2 weeks,
             maximum 3 weeks). It could also be detected here if the length
             of the iteration does not change often (It can change at the
             beginning and at the end of the project, but it should not
             change in the already started project).

Detection: Detect the beginning and end of the iteration and what is
           the interval between these time points. We should exclude
           the initial and final iterations, as they could skew the result.
*/

/* Init project id */
set @projectId = ?;
/* Exclude first and last iteration? */
set @excludeFirstAndLastIteration = true;
/* Id of first iteration */
set @idOfFirstIteration = (select id from iteration where iteration.superProjectId = @projectId order by name limit 1);
/* Id of last iteration */
set @idOfLastIteration = (select id from iteration where iteration.superProjectId = @projectId order by name desc limit 1);
/* Select all iterations with their length */
select datediff(endDate, startDate) as `iterationLength` from iteration where iteration.superProjectId = @projectId and iteration.id != if(@excludeFirstAndLastIteration = true, @idOfFirstIteration, -1) and iteration.id != if(@excludeFirstAndLastIteration = true, @idOfLastIteration, -1) order by iteration.name;
