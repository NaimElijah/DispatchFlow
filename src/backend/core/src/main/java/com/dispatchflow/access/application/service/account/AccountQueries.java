package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.AccessViews;
import com.dispatchflow.access.application.port.out.AccountView;
import com.dispatchflow.access.application.port.out.MeView;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.application.NotFound;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The account screens' queries: the signed-in user's own view, and the users list and one user
 * (FR-03, FR-181, FR-299; the permission matrix, "Read the company's details and its users").
 *
 * <p>A Driver whose other roles allow no more reads only their own account: {@link #list} answers
 * only it, and {@link #show} answers {@code NotFound} for any other, so it learns nothing of the
 * rest (the permission matrix; BR-02).
 *
 * <p>To Be Implemented.
 */
@Transactional(readOnly = true)
public class AccountQueries {

    private final AccessViews views;

    /**
     * Creates the query class.
     *
     * @param views the read models Access's screens read
     */
    public AccountQueries(AccessViews views) {
        this.views = views;
    }

    /**
     * The signed-in user's own view, with FR-299's warning for an Admin.
     *
     * <p>To Be Implemented.
     *
     * @param actor the signed-in user acting
     * @return the view
     * @throws NotFound when the actor's account no longer exists
     */
    public MeView me(Actor actor) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * The users the actor may read.
     *
     * <p>To Be Implemented.
     *
     * @param actor the signed-in user acting
     * @return every account, or only the actor's own for a Driver
     */
    public List<AccountView> list(Actor actor) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * One user.
     *
     * <p>To Be Implemented.
     *
     * @param actor the signed-in user acting
     * @param account the account
     * @return the account
     * @throws NotFound when no account has the ID, or the actor may read only their own and this is
     *     another
     */
    public AccountView show(Actor actor, AccountId account) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
