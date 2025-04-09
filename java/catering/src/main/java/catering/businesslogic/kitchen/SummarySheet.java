package catering.businesslogic.kitchen;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import catering.businesslogic.UseCaseLogicException;
import catering.businesslogic.event.Service;
import catering.businesslogic.menu.MenuItem;
import catering.businesslogic.recipe.Preparation;
import catering.businesslogic.shift.Shift;
import catering.businesslogic.user.User;
import catering.persistence.BatchUpdateHandler;
import catering.persistence.PersistenceManager;
import catering.persistence.ResultHandler;

public class SummarySheet {

    private int id;
    private Service serviceInfo;
    private User owner;
    private ArrayList<KitchenTask> taskList;
    private ArrayList<Assignment> assignmentList;

    private SummarySheet() {
    }

    public SummarySheet(Service service, User user) {

        this.serviceInfo = service;
        this.owner = user;
        taskList = new ArrayList<>();
        assignmentList = new ArrayList<>();

        ArrayList<MenuItem> menuItems = service.getMenuItems();

        for (MenuItem mi : menuItems) {
            // Add the main recipe task
            KitchenTask mainTask = new KitchenTask(mi.getRecipe(), mi.getDescription());
            taskList.add(mainTask);

            // Get all preparations from the recipe and add them as separate tasks
            if (mi.getRecipe() != null) {
                ArrayList<Preparation> preparations = mi.getRecipe().getPreparations();
                if (preparations != null) {
                    for (Preparation prep : preparations) {
                        // Create a task for each preparation with appropriate details
                        KitchenTask prepTask = new KitchenTask(prep, prep.getName());
                        taskList.add(prepTask);
                    }
                }
            }
        }
    }

    /**
     * Loads all summary sheets from the database
     * 
     * @return List of all summary sheets
     */
    public static ArrayList<SummarySheet> loadAllSumSheets() {
        // Using a parameterized query instead of concatenation
        String query = "SELECT * FROM SummarySheets";
        ArrayList<SummarySheet> summarySheets = new ArrayList<>();
        ArrayList<Integer> serviceIds = new ArrayList<>();
        ArrayList<Integer> ownerIds = new ArrayList<>();

        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                SummarySheet s = new SummarySheet();
                s.id = rs.getInt("id");

                summarySheets.add(s);
                serviceIds.add(rs.getInt("service_id"));
                ownerIds.add(rs.getInt("owner_id"));
            }
        });

        for (int i = 0; i < serviceIds.size(); i++) {
            summarySheets.get(i).serviceInfo = Service.loadById(serviceIds.get(i));
        }

        for (int i = 0; i < ownerIds.size(); i++) {
            SummarySheet s = summarySheets.get(i);
            s.owner = User.load(ownerIds.get(i));
            s.taskList = KitchenTask.loadAllTasksBySumSheetId(s.id);
            s.assignmentList = Assignment.loadAllAssignmentsBySumSheetId(s.id);
        }

        return summarySheets;
    }

    /**
     * Loads a specific summary sheet by ID
     * 
     * @param id The ID of the summary sheet to load
     * @return The loaded summary sheet, or null if not found
     */
    public static SummarySheet loadSummarySheetById(int id) {
        SummarySheet[] sheetHolder = new SummarySheet[1]; // Use array to allow modification in lambda
        String query = "SELECT * FROM SummarySheets WHERE id = ?";

        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                if (sheetHolder[0] != null)
                    return; // Only handle the first result

                SummarySheet s = new SummarySheet();
                s.id = rs.getInt("id");

                int serviceId = rs.getInt("service_id");
                s.serviceInfo = Service.loadById(serviceId);

                int ownerId = rs.getInt("owner_id");
                s.owner = User.load(ownerId);

                // Load related data
                s.taskList = KitchenTask.loadAllTasksBySumSheetId(s.id);
                s.assignmentList = Assignment.loadAllAssignmentsBySumSheetId(s.id);

                sheetHolder[0] = s;
            }
        }, id); // Pass id as parameter

        return sheetHolder[0];
    }

    /**
     * Loads summary sheets for a specific service
     * 
     * @param serviceId The ID of the service
     * @return List of summary sheets for the service
     */
    public static ArrayList<SummarySheet> loadSummarySheetsByServiceId(int serviceId) {
        ArrayList<SummarySheet> summarySheets = new ArrayList<>();
        String query = "SELECT * FROM SummarySheets WHERE service_id = ?";

        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                SummarySheet s = new SummarySheet();
                s.id = rs.getInt("id");

                int svcId = rs.getInt("service_id");
                s.serviceInfo = Service.loadById(svcId);

                int ownerId = rs.getInt("owner_id");
                s.owner = User.load(ownerId);

                // Load related data
                s.taskList = KitchenTask.loadAllTasksBySumSheetId(s.id);
                s.assignmentList = Assignment.loadAllAssignmentsBySumSheetId(s.id);

                summarySheets.add(s);
            }
        }, serviceId); // Pass serviceId as parameter

        return summarySheets;
    }

    public static void updateTaskList(SummarySheet ss) {
        String upd = "UPDATE Tasks SET position = ? WHERE id = ?";
        PersistenceManager.executeBatchUpdate(upd, ss.taskList.size(), new BatchUpdateHandler() {
            @Override
            public void handleBatchItem(PreparedStatement ps, int batchCount) throws SQLException {
                ps.setInt(1, batchCount);
                ps.setInt(2, ss.taskList.get(batchCount).getId());
            }

            @Override
            public void handleGeneratedIds(ResultSet rs, int count) throws SQLException {
                // no generated ids to handle
            }
        });
    }

    public int getTaskPosition(KitchenTask t) {
        return taskList.indexOf(t);
    }

    public static void saveNewSumSheet(SummarySheet s) {
        String sumSheetInsert = "INSERT INTO SummarySheets (service_id, owner_id) VALUES (?, ?);";
        int[] result = PersistenceManager.executeBatchUpdate(sumSheetInsert, 1, new BatchUpdateHandler() {
            @Override
            public void handleBatchItem(PreparedStatement ps, int batchCount) throws SQLException {
                ps.setInt(1, s.serviceInfo.getId());
                ps.setInt(2, s.owner.getId());
            }

            @Override
            public void handleGeneratedIds(ResultSet rs, int count) throws SQLException {
                // should be only one
                if (count == 0) {
                    s.id = rs.getInt(1);
                }
            }
        });

        if (result[0] > 0) {
            if (!s.assignmentList.isEmpty()) {
                Assignment.saveAllNewAssignment(s.id, s.assignmentList);
            }

            if (!s.taskList.isEmpty()) {
                KitchenTask.saveAllNewTasks(s.id, s.taskList);
            }
        }
    }

    public KitchenTask addTask(KitchenTask t) {
        this.taskList.add(t);
        return t;
    }

    public int getId() {
        return id;
    }

    public int getTaskListSize() {
        return taskList.size();
    }

    public void moveTask(KitchenTask t, int pos) {
        taskList.remove(t);
        taskList.add(pos, t);
    }

    public ArrayList<KitchenTask> getTaskList() {
        return taskList;
    }

    public Assignment addAssignment(KitchenTask t, Shift s, User cook) {
        Assignment ass = new Assignment(t, s, cook);
        assignmentList.add(ass);
        return ass;
    }

    public boolean isOwner(User user) {
        return user.equals(this.owner);
    }

    public Assignment modifyAssignment(Assignment ass, Shift shift, User cook) throws SummarySheetException {
        if (!assignmentList.contains(ass))
            throw new SummarySheetException("Invalid Assignment");
        ass.setShift(shift);
        ass.setCook(cook);
        return ass;
    }

    public ArrayList<Assignment> getAssignments() {
        return assignmentList;
    }

    public Assignment deleteAssignment(Assignment a) throws UseCaseLogicException {
        if (!assignmentList.contains(a))
            throw new UseCaseLogicException();
        return assignmentList.remove(assignmentList.indexOf(a));
    }

    public KitchenTask setTaskReady(KitchenTask t) throws UseCaseLogicException {
        if (!taskList.contains(t))
            throw new UseCaseLogicException();
        t.setReady();
        return t;
    }

    public KitchenTask addTaskInformation(KitchenTask task, int quantity, int portions, long minutes) {
        task.setQuantity(quantity);
        task.setPortions(portions);

        return task;
    }

    /**
     * Get the owner of this summary sheet
     * 
     * @return The User who owns this summary sheet
     */
    public User getOwner() {
        return owner;
    }

    /**
     * Returns a string representation of this summary sheet for testing purposes.
     * 
     * @return A string containing the key information of this summary sheet
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        // Basic summary sheet info
        sb.append("SummarySheet [ID: ").append(id);
        sb.append(", Owner: ").append(owner != null ? owner.getUserName() : "none");

        // Service info
        if (serviceInfo != null) {
            sb.append(", Service: ").append(serviceInfo.getName());
            sb.append(" (").append(serviceInfo.getId()).append(")");
        } else {
            sb.append(", No service");
        }

        // Task summary
        sb.append(", Tasks: ").append(taskList != null ? taskList.size() : 0);

        // Assignments summary
        sb.append(", Assignments: ").append(assignmentList != null ? assignmentList.size() : 0);

        // Task details
        if (taskList != null && !taskList.isEmpty()) {
            sb.append("]\nTasks:");
            int count = 1;
            for (KitchenTask task : taskList) {
                sb.append("\n  ").append(count++).append(". ");
                sb.append(task.isReady() ? "[✓] " : "[ ] ");
                sb.append(task.getDescription());

                // Add quantity and portions if set
                if (task.getQuantity() > 0 || task.getPortions() > 0) {
                    sb.append(" (");
                    if (task.getQuantity() > 0)
                        sb.append("Qty: ").append(task.getQuantity());
                    if (task.getQuantity() > 0 && task.getPortions() > 0)
                        sb.append(", ");
                    if (task.getPortions() > 0)
                        sb.append("Portions: ").append(task.getPortions());
                    sb.append(")");
                }
            }
        } else {
            sb.append("]");
        }

        // Assignment details if present
        if (assignmentList != null && !assignmentList.isEmpty()) {
            sb.append("\nAssignments:");
            int count = 1;
            for (Assignment ass : assignmentList) {
                sb.append("\n  ").append(count++).append(". ");

                // Task info
                KitchenTask task = ass.getTask();
                sb.append("Task: ").append(task != null ? task.getDescription() : "none");

                // Cook info
                User cook = ass.getCook();
                sb.append(", Cook: ").append(cook != null ? cook.getUserName() : "unassigned");

                // Shift info
                Shift shift = ass.getShift();
                if (shift != null) {
                    sb.append(", Shift: ").append(shift.getDate());
                    sb.append(" (").append(shift.getStartTime()).append("-").append(shift.getEndTime()).append(")");
                }
            }
        }

        return sb.toString();
    }
}
