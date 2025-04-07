package catering.businesslogic.user;

import catering.businesslogic.UseCaseLogicException;

public class UserManager {

    private User currentUser;

    public void fakeLogin(String username) throws UseCaseLogicException {
        this.currentUser = User.load(username);
        if (this.currentUser == null)
            throw new UseCaseLogicException("User not found");
    }

    public User getCurrentUser() {
        return this.currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }
}
