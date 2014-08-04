package com.offbynull.peernetic.demos.unstructured.messages.external;

import com.offbynull.peernetic.common.Response;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.commons.lang3.Validate;

public final class LinkResponse<A> extends Response {

    private boolean successful;
    private UnmodifiableList<A> links;

    public LinkResponse(boolean successful, List<A> links) {
        super(new byte[1]); // fake nonce which will get replaced later
        this.successful = successful;
        this.links = (UnmodifiableList<A>) UnmodifiableList.unmodifiableList(new ArrayList<A>(links));
        innerValidate();
    }

    public boolean isSuccessful() {
        return successful;
    }

    public UnmodifiableList<A> getLinks() {
        return links;
    }

    @Override
    protected void innerValidate() {
        try {
            Validate.noNullElements(links);
        } catch (RuntimeException re) {
            throw new IllegalStateException();
        }
    }

}
