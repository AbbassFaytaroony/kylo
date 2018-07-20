import * as angular from 'angular';
import * as _ from "underscore";
import AccessControlService from '../../../services/AccessControlService';
import { EntityAccessControlService } from '../../shared/entity-access-control/EntityAccessControlService';
import { Component, Inject, SimpleChanges } from '@angular/core';
import {CategoriesService} from '../../services/CategoriesService';
// const moduleName = require('feed-mgr/categories/module-name');

@Component({
    selector: 'thinkbig-category-feed-properties',
    templateUrl: 'js/feed-mgr/categories/details/category-feed-properties.html'
})
export class CategoryFeedPropertiesController {

    /**
    * Indicates if the properties may be edited.
    */
    allowEdit:boolean = false;
    /**
    * Category data used in "edit" mode.
    * @type {CategoryModel}
    */
    editModel:any;
    /**
    * Indicates if the view is in "edit" mode.
    * @type {boolean} {@code true} if in "edit" mode or {@code false} if in "normal" mode
    */
    isEditable:boolean = false;
    /**
    * Indicates of the category is new.
    * @type {boolean}
    */
    isNew:boolean = true;
    /**
    * Indicates if the properties are valid and can be saved.
    * @type {boolean} {@code true} if all properties are valid, or {@code false} otherwise
    */
    isValid:boolean = true;
    /**
    * Category data used in "normal" mode.
    * @type {CategoryModel}
    */
    model:any;

    ngOnInit() {
        this.model = this.CategoriesService.model;

        //Apply the entity access permissions
        this.$injector.get("$q").when(this.accessControlService.hasPermission(AccessControlService.CATEGORIES_ADMIN,this.model,AccessControlService.ENTITY_ACCESS.CATEGORY.EDIT_CATEGORY_DETAILS)).then((access:any) =>{
            this.allowEdit = access;
        });
    }

    /**
     * Manages the Category Feed Properties section of the Category Details page.
     *
     * @constructor
     * @param {AccessControlService} AccessControlService the access control service
     * @param CategoriesService the category service
     */
    constructor(private accessControlService:AccessControlService,
                private entityAccessControlService:EntityAccessControlService, 
                private CategoriesService:CategoriesService,
                @Inject("$injector") private $injector: any) {

        this.editModel = CategoriesService.newCategory();

        // $scope.$watch(
        //     () =>{
        //         return CategoriesService.model.id
        //     },
        //     (newValue:any) =>{
        //         this.isNew = !angular.isString(newValue)
        //     }
        // );
    }
    /**
         * Switches to "edit" mode.
         */
        onEdit = () => {
            this.editModel = angular.copy(this.model);
        };

        /**
         * Saves the category properties.
         */
        onSave = () => {
            var model = angular.copy(this.CategoriesService.model);
            model.id = this.model.id;
            model.userFields = this.editModel.userFields;
            model.userProperties = null;

            this.CategoriesService.save(model).then((response:any) =>{
                this.model = this.CategoriesService.model = response.data;
                this.CategoriesService.update(response.data);
                this.$injector.get("$mdToast").show(
                    this.$injector.get("$mdToast").simple()
                        .textContent('Saved the Category')
                        .hideDelay(3000)
                );
            }, (err:any) => {
                this.$injector.get("$mdDialog").show(
                    this.$injector.get("$mdDialog").alert()
                        .clickOutsideToClose(true)
                        .title("Save Failed")
                        .textContent("The category '" + model.name + "' could not be saved. " + err.data.message)
                        .ariaLabel("Failed to save category")
                        .ok("Got it!")
                );
            });
        };
}