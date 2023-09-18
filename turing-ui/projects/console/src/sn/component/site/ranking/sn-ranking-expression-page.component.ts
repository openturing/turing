import {Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {Observable, tap} from 'rxjs';
import {TurSNSite} from '../../../model/sn-site.model';
import {NotifierService} from 'angular-notifier';
import {TurSNSiteService} from '../../../service/sn-site.service';
import {ActivatedRoute, Router} from '@angular/router';
import {TurSNRankingExpression} from "../../../model/sn-ranking-expression.model";
import {TurSNRankingExpressionService} from "../../../service/sn-ranking-expression.service";
import {TurSNSiteField} from "../../../model/sn-site-field.model";
import {TurSNRankingCondition} from "../../../model/sn-ranking-condition.model";

@Component({
  selector: 'sn-site-ranking-expression-page',
  templateUrl: './sn-ranking-expression-page.component.html'
})
export class TurSNRankingExpressionPageComponent implements OnInit {
  @ViewChild('modalDeleteRankingExpression')
  modalDelete!: ElementRef;
  private readonly turSNSite: Observable<TurSNSite>;
  private readonly turSNRankingExpression: Observable<TurSNRankingExpression>;
  private turSNSiteSEFields: TurSNSiteField[] = new Array<TurSNSiteField>;
  private readonly siteId: string;
  private readonly newObject: boolean = false;
  private deletedConditions: Array<string> = new Array<string>;

  constructor(
    private readonly notifier: NotifierService,
    private turSNSiteService: TurSNSiteService,
    private turSNRankingExpressionService: TurSNRankingExpressionService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {
    this.siteId = this.activatedRoute.parent?.parent?.snapshot.paramMap.get('id') || "";
    let rankingExpressionId = this.activatedRoute.snapshot.paramMap.get('rankingExpressionId') || "";
    this.turSNSite = this.turSNSiteService.get(this.siteId);
    this.newObject = (rankingExpressionId.toLowerCase() === 'new');
    this.turSNRankingExpression = this.newObject ? this.turSNRankingExpressionService.getStructure(this.siteId) :
        this.turSNRankingExpressionService.get(this.siteId, rankingExpressionId);
    turSNSiteService.getFieldsByType(this.siteId, "se").subscribe(fields => {
      this.turSNSiteSEFields = fields as TurSNSiteField[]
    });
  }

  getTurSNSite(): Observable<TurSNSite> {
    return this.turSNSite;
  }
  getTurSNRankingExpression(): Observable<TurSNRankingExpression> {
    return this.turSNRankingExpression;
  }
  getTurSNSiteSEFields(): TurSNSiteField[] {
    return this.turSNSiteSEFields;
  }

  getFieldType(fieldName: string): string {
    return <string>this.turSNSiteSEFields.find(field => field.name == fieldName)?.type;
  }
  newCondition(turSNRankingConditions: TurSNRankingCondition[]) {
    let turSNRankingCondition  = new TurSNRankingCondition();
    turSNRankingCondition.condition = 1;
    turSNRankingConditions.push(turSNRankingCondition);

  }

  removeCondition(snRankingExpression: TurSNRankingExpression, snRankingCondition: TurSNRankingCondition) {
    this.deletedConditions.push(snRankingCondition.id);
    snRankingExpression.turSNRankingConditions = snRankingExpression.turSNRankingConditions.filter(condition => condition != snRankingCondition)
  }
  ngOnInit(): void {
    // Empty
  }

  isNewObject(): boolean {
    return this.newObject;
  }

  saveButtonCaption(): string {
    return this.newObject ? "Create rule" : "Update rule";
  }

  public save(_turSNRankingExpression: TurSNRankingExpression) {
    this.turSNRankingExpressionService.save(this.siteId, _turSNRankingExpression, this.newObject).subscribe(
      (turSNRankingExpression: TurSNRankingExpression) => {
        let message: string = this.newObject ? " ranking expression was created." : " ranking expression was updated.";

        _turSNRankingExpression = turSNRankingExpression;

        this.notifier.notify("success", turSNRankingExpression.name.concat(message));

        this.router.navigate(['/sn/site/', this.siteId, 'ranking expression', 'list']);
      },
      response => {
        this.notifier.notify("error", "Ranking Expression was error: " + response);
      },
      () => {
        // The POST observable is now completed.
      });
  }

  public delete(_turSNRankingExpression: TurSNRankingExpression) {
    this.turSNRankingExpressionService.delete(this.siteId, _turSNRankingExpression).subscribe(
      (turSNRankingExpression: TurSNRankingExpression) => {
        this.notifier.notify("success", _turSNRankingExpression.name.concat(" ranking expression was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");

       // this.router.navigate(['/sn/site/', this.siteId, 'ranking-expression', 'list']);
      },
      response => {
        this.notifier.notify("error", "Ranking Expression was error: " + response);
      },
      () => {
        // The POST observable is now completed.
      });
  }
}
