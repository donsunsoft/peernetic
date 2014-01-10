package com.offbynull.peernetic.overlay.chord.tasks;

import com.offbynull.peernetic.actor.EndpointFinder;
import com.offbynull.peernetic.actor.helpers.AbstractChainedTask;
import com.offbynull.peernetic.actor.helpers.Task;
import com.offbynull.peernetic.overlay.chord.core.ChordState;
import com.offbynull.peernetic.overlay.common.id.Id;
import com.offbynull.peernetic.overlay.common.id.Pointer;
import org.apache.commons.lang3.Validate;

public final class FindSuccessorTask<A> extends AbstractChainedTask {
    private Id findId;
    private ChordState<A> chordState;
    private Stage stage;
    
    private EndpointFinder<A> finder;
    
    private Pointer<A> result;

    public FindSuccessorTask(Id findId, ChordState<A> chordState, EndpointFinder<A> finder) {
        Validate.notNull(findId);
        Validate.notNull(chordState);
        Validate.notNull(finder);

        this.findId = findId;
        this.chordState = chordState;
        this.finder = finder;
        
        stage = Stage.INITIAL;
    }

    @Override
    protected Task switchTask(Task prev) {
        if (prev != null && prev.getState() == TaskState.FAILED) {
            setFinished(true);
            return null;
        }
        
        switch (stage) {
            case INITIAL: {
                stage = Stage.FIND_PREDECESSOR;
                return new FindPredecessorTask(findId, chordState, finder);
            }
            case FIND_PREDECESSOR: {
                Pointer<A> pointer = ((FindPredecessorTask) prev).getResult();
                stage = Stage.FIND_SUCCESSOR;
                return new GetSuccessorTask(pointer, finder);
            }
            case FIND_SUCCESSOR: {
                result = ((GetSuccessorTask) prev).getResult();
                setFinished(false);
                return null;
            }
            default:
                throw new IllegalArgumentException();
        }
    }

    public Pointer<A> getResult() {
        return result;
    }

    private enum Stage {
        INITIAL,
        FIND_PREDECESSOR,
        FIND_SUCCESSOR
    }

}
