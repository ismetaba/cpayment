package com.cpayment.custody.domain.port;

import com.cpayment.custody.domain.model.ApprovalProof;
import com.cpayment.custody.domain.model.PendingApproval;
import com.cpayment.custody.domain.model.TransferId;

import java.util.List;

/**
 * Optional capability — only present when capabilities().approval() != AUTO.
 * When AUTO, no implementation is bound and cpayment never references this port.
 */
public interface ApprovalPort {
    List<PendingApproval> listPendingApprovals();
    void approveTransfer(TransferId id, ApprovalProof proof);
    void rejectTransfer(TransferId id, String reason);
}
