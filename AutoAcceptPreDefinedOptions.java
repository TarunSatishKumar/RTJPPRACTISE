package com.apporchid.docuthink.transformer;

import java.util.Arrays;
import java.util.List;

import com.apporchid.cloudseer.common.transformer.BaseTransformer;
import com.apporchid.cloudseer.common.transformer.properties.TransformerProperties;
import com.apporchid.common.utils.ContextHelper;
import com.apporchid.contract.common.EContractStatus;
import com.apporchid.contract.common.EProjectStatus;
import com.apporchid.contract.common.constants.IContractCommonConstants;
import com.apporchid.contract.common.service.ContractCommonService;
import com.apporchid.docuthink.constants.ICollaborationCommonConstants;
import com.apporchid.docuthink.constants.ICollaborationPipelineConstants;
import com.apporchid.foundation.mso.IMSODataObject;
import com.apporchid.foundation.transformer.exception.TransformerInitializationException;
import com.apporchid.jpa.service.ConfigurationService;
import com.apporchid.reviewer.constants.IReviewerQryConstants;

public class AutoAcceptPreDefinedOptions extends BaseTransformer<TransformerProperties> implements ICollaborationPipelineConstants, IReviewerQryConstants {

	private static final long serialVersionUID = 8659185272880886376L;
	private boolean autoAcceptPreDefinedOptions = false;
	protected ConfigurationService configurationService;
	protected ContractCommonService contractCommonService;

	public AutoAcceptPreDefinedOptions(TransformerProperties transformerProperties) {
		super(transformerProperties);
	}

	@Override
	protected void initalizeTransformer() throws TransformerInitializationException {
		super.initalizeTransformer();
		configurationService =  ContextHelper.getBean(ConfigurationService.class);
		contractCommonService = ContextHelper.getBean(ContractCommonService.class);
		String autoAcceptStr = configurationService.getConfigurationPropertyByNameAndGroup(
				IContractCommonConstants.AUTO_ACCEPT_PRE_DEFINED_OPTIONS, ICollaborationCommonConstants.DEFAULT_CONFIGURATION_GROUP);
		autoAcceptPreDefinedOptions = Boolean.parseBoolean( autoAcceptStr);
	}

	@Override
	protected Object transform(IMSODataObject msoDataObject) {
		Long contractId = msoDataObject.getFieldValue("contractId", Long.class);
		String contractStatus = msoDataObject.getFieldValue("status", String.class);
		if (!EContractStatus.DECLINED.getName().equals(contractStatus)) {
			contractCommonService.executeUpdateOrDeleteStatement(UPDATE_NEEDS_REVIEW_FLAG, null, Arrays.asList(contractId));
			if (autoAcceptPreDefinedOptions) {
				// auto accept predefined constants
				contractCommonService.executeUpdateOrDeleteStatement(UPDATE_IS_REVIEWED_FLAG, null, Arrays.asList(contractId));
				//update review count
				contractCommonService.executeUpdateOrDeleteStatement(QRY_UPDATE_REVIEWED_COUNT, null, Arrays.asList(contractId));
				contractCommonService.executeUpdateOrDeleteStatement(UPDATE_REVIEW_TOTALS, null, Arrays.asList(contractId));
			}
		}else {
			contractCommonService.executeUpdateOrDeleteStatement(QRY_UPDATE_DECLINED_CONTRACT_REVIEWED_COUNT, null, Arrays.asList(contractId));
		}
		List<IMSODataObject> list = contractCommonService.getData(CHECK_PROJECT_COMPLETE_STATUS,null,Arrays.asList(contractId));
		//It is null when all clauses are reviewed from all contracts
		if(list != null ) {
			Long projectId =0l; 
			boolean isAllReviewed = true;
			boolean isAllSigned = true;
			for(IMSODataObject data : list) {
				String status = data.getFieldValue("status",String.class);
				if(projectId <= 0) {
					projectId = data.getFieldValue("projectId",Long.class);
				}
				Long id = data.getFieldValue("id",Long.class);
				if(id.equals(contractId)) {
					status = EContractStatus.SIGNED.getName();
				}
				int totalReviewedClauses = data.getFieldValue("totalReviewedClauses",Integer.class, 0);
				int totalReviewClauses = data.getFieldValue("totalReviewClauses",Integer.class, 0);
				if(totalReviewClauses != totalReviewedClauses  ) {
					isAllReviewed = false;
				} 
				if(status.equals(EContractStatus.PENDING.getName())) {
					isAllReviewed = false;
					isAllSigned = false;
				}
				if(status.equals(EContractStatus.INPROGRESS.getName()) && !id.equals(contractId)) {
					isAllReviewed = false;
					isAllSigned = false;
				}
			}
			
			if(isAllSigned && !isAllReviewed) {
				contractCommonService.executeUpdateOrDeleteStatement(UPDATE_PROJECT_STATUS_TO_IN_REVIEW, null, Arrays.asList(contractId));
				contractCommonService.saveProjectTimeline(projectId, EProjectStatus.INREVIEW.getName());
			}
			if(isAllReviewed) {
				contractCommonService.executeUpdateOrDeleteStatement(UPDATE_PROJECT_STATUS_TO_COMPLETE, null, Arrays.asList(contractId));
				contractCommonService.saveProjectTimeline(projectId, EProjectStatus.COMPLETE.getName());
			}
		}
		return msoDataObject;
	}
}
